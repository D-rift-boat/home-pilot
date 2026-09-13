package com.dboat.user.security;

import com.dboat.user.entity.User;
import com.dboat.user.entity.UserAuth;
import com.dboat.user.enums.AuthCodeEnum;
import com.dboat.user.enums.UserStatusEnum;
import com.dboat.user.exception.AuthException;
import com.dboat.user.service.UserAuthorityService;
import com.dboat.user.service.UserAuthService;
import com.dboat.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 认证用户加载服务实现
 * <p>
 * 账号定位链路：{@code user_auth.identifier} → {@code user.username} 兜底，
 * 命中后回查 {@code user} 主表与 RBAC 角色 / 权限集合，组装 {@link LoginUser}。
 * </p>
 * <p>
 * 锁定自愈：数据库中 {@code status=2} 且 {@code lock_expire_time} 已过期的账号，
 * 加载时自动恢复正常状态，避免因 Redis 数据丢失导致用户被永久锁死。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Service
public class AuthUserDetailsServiceImpl implements AuthUserDetailsService {

    /** 用户主表服务 */
    @Resource
    private UserService userService;

    /** 用户认证凭据服务 */
    @Resource
    private UserAuthService userAuthService;

    /** 角色 / 权限 / 版本号缓存服务 */
    @Resource
    private UserAuthorityService userAuthorityService;

    /**
     * 按登录标识加载认证主体
     *
     * @param orgId      租户ID，可为空
     * @param identifier 登录标识（账号 / 手机号 / 邮箱）
     * @return 认证主体
     */
    @Override
    public LoginUser loadByIdentifier(String orgId, String identifier) {
        if (!StringUtils.hasText(identifier)) {
            throw new AuthException(AuthCodeEnum.ACCOUNT_NOT_FOUND);
        }

        // ===== 1. 优先按 user_auth.identifier 匹配（手机号 / 邮箱 / 账号） =====
        List<UserAuth> authList = userAuthService.listPasswordAuthByIdentifier(orgId, identifier);
        UserAuth userAuth = resolveSingleAuth(authList, orgId, identifier);

        // ===== 2. 未命中则按 user.username 兜底匹配 =====
        if (userAuth == null) {
            User userByUsername = userService.getByOrgIdAndUsername(orgId, identifier);
            if (userByUsername == null) {
                throw new AuthException(AuthCodeEnum.ACCOUNT_NOT_FOUND);
            }
            userAuth = userAuthService.getPasswordAuthByUserId(userByUsername.getUserId());
            if (userAuth == null) {
                // 账号存在但未设置密码凭据，等同于密码登录不可用
                log.warn("【认证】账号无密码凭据 userId={}", userByUsername.getUserId());
                throw new AuthException(AuthCodeEnum.ACCOUNT_NOT_FOUND);
            }
        }

        User user = userService.getUserById(userAuth.getUserId());
        if (user == null) {
            throw new AuthException(AuthCodeEnum.ACCOUNT_NOT_FOUND);
        }
        return buildLoginUser(user, userAuth.getCredential());
    }

    /**
     * 按用户ID加载认证主体
     *
     * @param userId 用户ID
     * @return 认证主体，用户不存在时返回 null
     */
    @Override
    public LoginUser loadByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        User user = userService.getUserById(userId);
        if (user == null) {
            return null;
        }
        UserAuth userAuth = userAuthService.getPasswordAuthByUserId(userId);
        return buildLoginUser(user, userAuth == null ? null : userAuth.getCredential());
    }

    /**
     * Spring Security 标准加载入口
     *
     * @param username 登录标识
     * @return 认证主体
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return loadByIdentifier(null, username);
        } catch (AuthException e) {
            // 转换为 Spring Security 契约异常，交由 DaoAuthenticationProvider 处理
            throw new UsernameNotFoundException(e.getMessage(), e);
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 从候选凭据中解析唯一匹配项
     * <p>
     * 跨租户场景下同一标识可能命中多条，此时必须要求前端显式指定 orgId，
     * 否则无法确定用户归属租户，会造成越权风险。
     * </p>
     *
     * @param authList   候选凭据列表
     * @param orgId      请求指定的租户ID
     * @param identifier 登录标识
     * @return 唯一凭据，未命中返回 null
     */
    private UserAuth resolveSingleAuth(List<UserAuth> authList, String orgId, String identifier) {
        if (CollectionUtils.isEmpty(authList)) {
            return null;
        }
        if (authList.size() > 1 && !StringUtils.hasText(orgId)) {
            log.warn("【认证】登录标识跨租户命中 {} 个账号，需指定 orgId identifier={}", authList.size(), identifier);
            throw new AuthException(AuthCodeEnum.ORG_REQUIRED);
        }
        return authList.get(0);
    }

    /**
     * 组装认证主体：加载账号状态、令牌版本与 RBAC 角色 / 权限集合
     *
     * @param user          用户主表实体
     * @param encodedPassword BCrypt 密码哈希，可为空
     * @return 认证主体
     */
    private LoginUser buildLoginUser(User user, String encodedPassword) {
        // ===== 锁定自愈：锁定已过期则自动恢复，避免 Redis 数据丢失导致永久锁定 =====
        Integer status = unlockIfExpired(user);

        Integer permVersion = userAuthorityService.getPermVersion(user.getUserId());
        return LoginUser.builder()
                .userId(user.getUserId())
                .orgId(user.getOrgId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .password(encodedPassword)
                .status(status)
                .tokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .permVersion(permVersion == null ? 0 : permVersion)
                .firstLogin(user.getFirstLogin())
                .roleKeys(new LinkedHashSet<>(userAuthorityService.getRoleKeys(user.getOrgId(), user.getUserId())))
                .perms(new LinkedHashSet<>(userAuthorityService.getPermCodes(user.getOrgId(), user.getUserId())))
                .build();
    }

    /**
     * 账号锁定状态自愈
     * <p>{@code status=2} 且 {@code lock_expire_time} 已过期时，重置失败计数并恢复正常状态。</p>
     *
     * @param user 用户主表实体
     * @return 自愈后的账号状态
     */
    private Integer unlockIfExpired(User user) {
        Integer status = user.getStatus();
        if (status == null || status != UserStatusEnum.LOCKED.getCode()) {
            return status;
        }
        LocalDateTime lockExpireTime = user.getLockExpireTime();
        if (lockExpireTime == null || lockExpireTime.isAfter(LocalDateTime.now())) {
            return status;
        }
        userService.resetLoginFailState(user.getUserId());
        log.info("【认证】账号锁定已到期，自动解除锁定 userId={}", user.getUserId());
        return UserStatusEnum.NORMAL.getCode();
    }
}
