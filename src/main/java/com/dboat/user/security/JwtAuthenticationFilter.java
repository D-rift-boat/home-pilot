package com.dboat.user.security;

import com.dboat.user.common.constants.AuthConstants;
import com.dboat.user.enums.AuthCodeEnum;
import com.dboat.user.enums.UserStatusEnum;
import com.dboat.user.service.TokenSessionService;
import com.dboat.user.service.UserAuthorityService;
import com.dboat.user.utils.AuthWebUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JWT 认证过滤器
 * <p>
 * 无状态认证链路，每个请求执行一次：
 * <ol>
 *   <li>从 {@code Authorization: Bearer xxx} 提取 Access Token，缺失则直接放行（由授权规则决定 401）</li>
 *   <li>RS256 公钥验签解析，区分「已过期」与「签名非法」两类失败并返回不同业务码</li>
 *   <li>查 Access Token 黑名单（登出 / 踢人后在自然过期前主动失效）</li>
 *   <li>比对令牌版本号（{@code user.token_version}），不一致即判定令牌失效</li>
 *   <li>从 Redis 缓存加载角色与权限集合，组装 {@link LoginUser} 写入 SecurityContext</li>
 * </ol>
 * </p>
 * <p>
 * 性能约束：整条链路只访问 Redis，不查数据库。账号被禁用 / 封号时，
 * 必须由管理端执行 {@code token_version + 1} 并撤销全部会话，本过滤器通过版本号比对感知变更。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 令牌提供者 */
    @Resource
    private JwtTokenProvider jwtTokenProvider;

    /** 角色 / 权限 / 版本号缓存服务 */
    @Resource
    private UserAuthorityService userAuthorityService;

    /** 会话与令牌管理服务（黑名单校验） */
    @Resource
    private TokenSessionService tokenSessionService;

    /**
     * 过滤器主逻辑
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String accessToken = resolveToken(request);
        // ===== 未携带令牌：交由 SecurityConfig 的授权规则与 401 处理器决定 =====
        if (!StringUtils.hasText(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims;
        try {
            claims = jwtTokenProvider.parseAccessToken(accessToken);
        } catch (ExpiredJwtException e) {
            // 令牌过期属于正常生命周期，前端应静默使用 Refresh Token 换取新令牌
            log.debug("【JWT认证】Access Token 已过期 jti={}", e.getClaims().getId());
            AuthResponseWriter.writeUnauthorized(response, AuthCodeEnum.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("【JWT认证】Access Token 验签失败 uri={}, cause={}", request.getRequestURI(), e.getMessage());
            AuthResponseWriter.writeUnauthorized(response, AuthCodeEnum.TOKEN_INVALID);
            return;
        }

        String userId = claims.getSubject();
        String sessionId = claims.getId();

        // ===== 1. 黑名单校验：登出 / 踢人后的令牌在自然过期前主动失效 =====
        if (tokenSessionService.isBlacklisted(sessionId)) {
            log.warn("【JWT认证】Access Token 已被撤销 userId={}, sessionId={}", userId, sessionId);
            AuthResponseWriter.writeUnauthorized(response, AuthCodeEnum.TOKEN_REVOKED);
            return;
        }

        // ===== 2. 令牌版本号校验：改密 / 封号 / 踢人后旧令牌全部失效 =====
        Integer currentTokenVersion = userAuthorityService.getTokenVersion(userId);
        Integer tokenVersion = claims.get(AuthConstants.CLAIM_TOKEN_VERSION, Integer.class);
        if (currentTokenVersion != null && tokenVersion != null && !currentTokenVersion.equals(tokenVersion)) {
            log.warn("【JWT认证】令牌版本已变更，强制重新登录 userId={}, tokenVersion={} -> {}",
                    userId, tokenVersion, currentTokenVersion);
            AuthResponseWriter.writeUnauthorized(response, AuthCodeEnum.TOKEN_REVOKED);
            return;
        }

        // ===== 3. 组装认证主体并写入 SecurityContext =====
        LoginUser loginUser = buildLoginUser(claims, userId, sessionId, request);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginUser, null, loginUser.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 无状态认证：请求结束立即清理，避免线程复用时上下文串号
            SecurityContextHolder.clearContext();
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 从请求头提取 Bearer 令牌
     *
     * @param request HTTP 请求
     * @return 令牌字符串，缺失时返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AuthConstants.HEADER_AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(AuthConstants.BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(AuthConstants.BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * 由 JWT 声明 + Redis 缓存组装认证主体
     * <p>
     * 租户、账号、昵称等静态信息直接取自令牌声明，角色与权限取自 Redis 缓存，
     * 全流程零数据库访问。令牌已通过验签与版本号校验，故账号状态视为正常。
     * </p>
     *
     * @param claims    JWT 声明集合
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param request   HTTP 请求
     * @return 认证主体
     */
    private LoginUser buildLoginUser(Claims claims, String userId, String sessionId, HttpServletRequest request) {
        String orgId = claims.get(AuthConstants.CLAIM_ORG_ID, String.class);
        Integer permVersion = claims.get(AuthConstants.CLAIM_PERM_VERSION, Integer.class);
        Integer tokenVersion = claims.get(AuthConstants.CLAIM_TOKEN_VERSION, Integer.class);

        // 优先使用 Redis 缓存中的最新角色 / 权限，授权变更后无需等待令牌过期
        Set<String> roleKeys = new LinkedHashSet<>(userAuthorityService.getRoleKeys(orgId, userId));
        Set<String> perms = new LinkedHashSet<>(userAuthorityService.getPermCodes(orgId, userId));

        return LoginUser.builder()
                .userId(userId)
                .orgId(orgId)
                .username(claims.get(AuthConstants.CLAIM_USERNAME, String.class))
                .nickname(claims.get(AuthConstants.CLAIM_NICKNAME, String.class))
                .status(UserStatusEnum.NORMAL.getCode())
                .tokenVersion(tokenVersion)
                .permVersion(permVersion)
                .sessionId(sessionId)
                .roleKeys(roleKeys)
                .perms(perms)
                .loginTs(claims.getIssuedAt() == null ? null : claims.getIssuedAt().getTime())
                .loginIp(AuthWebUtils.getClientIp(request))
                .build();
    }
}
