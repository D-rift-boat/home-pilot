package com.dboat.user.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dboat.user.common.constants.AuthLuaConstants;
import com.dboat.user.config.properties.AuthProperties;
import com.dboat.user.dto.session.RefreshConsumeResult;
import com.dboat.user.dto.session.UserSessionDTO;
import com.dboat.user.security.LoginUser;
import com.dboat.user.service.TokenSessionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_ACCESS_BLACKLIST;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_REFRESH_TOKEN;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_SESSION_DETAIL;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_USER_SESSION_ZSET;

/**
 * 登录会话与 Refresh Token 管理服务实现
 * <p>
 * 所有「读-判断-写」的多步操作均通过 Lua 脚本保证原子性，
 * 避免并发刷新导致的令牌重放漏检与会话数超限。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Service
public class TokenSessionServiceImpl implements TokenSessionService {

    /** Lua 脚本：Refresh Token 原子消费 + 重放检测 */
    private final DefaultRedisScript<String> refreshConsumeScript =
            new DefaultRedisScript<>(AuthLuaConstants.LUA_REFRESH_CONSUME, String.class);

    /** Lua 脚本：新增会话并裁剪超限会话 */
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> sessionLimitScript =
            new DefaultRedisScript<>(AuthLuaConstants.LUA_SESSION_ADD_WITH_LIMIT, List.class);

    /** Redis 操作模板 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /**
     * 创建登录会话
     *
     * @param user         登录用户主体
     * @param sessionId    会话ID
     * @param refreshToken Refresh Token
     * @param loginIp      登录客户端IP
     * @param userAgent    客户端 UA
     * @param channel      登录渠道
     * @param loginType    登录类型
     * @return 已落库的会话信息
     */
    @Override
    public UserSessionDTO createSession(LoginUser user, String sessionId, String refreshToken,
                                        String loginIp, String userAgent, Integer channel, Integer loginType) {
        long now = System.currentTimeMillis();
        long ttlSeconds = authProperties.getJwt().getRefreshTokenTtlSeconds();

        UserSessionDTO session = UserSessionDTO.builder()
                .sessionId(sessionId)
                .userId(user.getUserId())
                .orgId(user.getOrgId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .refreshToken(refreshToken)
                .roleKeys(user.getRoleKeys() == null ? new ArrayList<>() : new ArrayList<>(user.getRoleKeys()))
                .tokenVersion(user.getTokenVersion())
                .permVersion(user.getPermVersion())
                .loginIp(loginIp)
                .userAgent(userAgent)
                .channel(channel)
                .loginType(loginType)
                .loginTs(now)
                .expireTs(now + ttlSeconds * 1000L)
                .used(0)
                .build();

        String sessionJson = JSON.toJSONString(session);
        // ===== 1. Refresh Token → 会话映射（轮换与撤销的入口） =====
        stringRedisTemplate.opsForValue().set(String.format(AUTH_REFRESH_TOKEN, refreshToken),
                sessionJson, ttlSeconds, TimeUnit.SECONDS);
        // ===== 2. 会话详情（登出、会话列表、踢人使用） =====
        stringRedisTemplate.opsForValue().set(String.format(AUTH_SESSION_DETAIL, sessionId),
                sessionJson, ttlSeconds, TimeUnit.SECONDS);

        // ===== 3. 用户会话索引 + 并发会话裁剪（Lua 原子执行） =====
        List<String> evictedSessionIds = addSessionWithLimit(user.getUserId(), sessionId, now, ttlSeconds);
        for (String evictedSessionId : evictedSessionIds) {
            log.info("【会话管理】用户并发会话超限，踢掉最早会话 userId={}, evictedSessionId={}",
                    user.getUserId(), evictedSessionId);
            revokeSession(evictedSessionId);
        }
        return session;
    }

    /**
     * 查询会话详情
     *
     * @param sessionId 会话ID
     * @return 会话信息，不存在时返回 null
     */
    @Override
    public UserSessionDTO getSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(String.format(AUTH_SESSION_DETAIL, sessionId));
        return StringUtils.hasText(json) ? JSON.parseObject(json, UserSessionDTO.class) : null;
    }

    /**
     * 查询用户当前全部有效会话
     *
     * @param userId 用户ID
     * @return 会话列表（按登录时间升序）
     */
    @Override
    public List<UserSessionDTO> listSessions(String userId) {
        Set<String> sessionIds = stringRedisTemplate.opsForZSet()
                .range(String.format(AUTH_USER_SESSION_ZSET, userId), 0, -1);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量读取会话详情，避免 N 次网络往返
        List<String> keys = sessionIds.stream()
                .map(sessionId -> String.format(AUTH_SESSION_DETAIL, sessionId))
                .toList();
        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(keys);
        if (jsonList == null) {
            return Collections.emptyList();
        }
        List<UserSessionDTO> sessions = new ArrayList<>();
        for (String json : jsonList) {
            if (StringUtils.hasText(json)) {
                sessions.add(JSON.parseObject(json, UserSessionDTO.class));
            }
        }
        return sessions;
    }

    /**
     * 原子消费 Refresh Token（轮换 + 重放检测）
     *
     * @param refreshToken 待消费的 Refresh Token
     * @return 消费结果
     */
    @Override
    public RefreshConsumeResult consumeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return RefreshConsumeResult.builder()
                    .status(RefreshConsumeResult.Status.NOT_FOUND)
                    .build();
        }
        String result = stringRedisTemplate.execute(refreshConsumeScript,
                Collections.singletonList(String.format(AUTH_REFRESH_TOKEN, refreshToken)));
        if (!StringUtils.hasText(result)) {
            return RefreshConsumeResult.builder()
                    .status(RefreshConsumeResult.Status.NOT_FOUND)
                    .build();
        }

        JSONObject resultObj = JSON.parseObject(result);
        // ===== 重放攻击：已作废的旧令牌被再次使用 =====
        if (resultObj.getIntValue("replay") == 1) {
            log.warn("【令牌安全】检测到 Refresh Token 重放，判定凭证泄漏 userId={}, orgId={}",
                    resultObj.getString("userId"), resultObj.getString("orgId"));
            return RefreshConsumeResult.builder()
                    .status(RefreshConsumeResult.Status.REPLAY)
                    .userId(resultObj.getString("userId"))
                    .orgId(resultObj.getString("orgId"))
                    .build();
        }

        // ===== 消费成功：返回原会话信息 =====
        UserSessionDTO session = JSON.parseObject(resultObj.getString("data"), UserSessionDTO.class);
        return RefreshConsumeResult.builder()
                .status(RefreshConsumeResult.Status.SUCCESS)
                .userId(session.getUserId())
                .orgId(session.getOrgId())
                .session(session)
                .build();
    }

    /**
     * 绑定新的 Refresh Token 到会话
     *
     * @param session    会话信息（需已设置新的 refreshToken）
     * @param ttlSeconds 新令牌有效期（秒）
     */
    @Override
    public void bindRefreshToken(UserSessionDTO session, long ttlSeconds) {
        session.setUsed(0);
        String sessionJson = JSON.toJSONString(session);
        stringRedisTemplate.opsForValue().set(String.format(AUTH_REFRESH_TOKEN, session.getRefreshToken()),
                sessionJson, ttlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(String.format(AUTH_SESSION_DETAIL, session.getSessionId()),
                sessionJson, ttlSeconds, TimeUnit.SECONDS);
        // 会话索引续期，保证索引与令牌生命周期一致
        stringRedisTemplate.opsForZSet().add(String.format(AUTH_USER_SESSION_ZSET, session.getUserId()),
                session.getSessionId(), session.getLoginTs() == null ? System.currentTimeMillis() : session.getLoginTs());
        stringRedisTemplate.expire(String.format(AUTH_USER_SESSION_ZSET, session.getUserId()),
                ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 撤销单个会话
     *
     * @param sessionId 会话ID
     */
    @Override
    public void revokeSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        UserSessionDTO session = getSession(sessionId);
        stringRedisTemplate.delete(String.format(AUTH_SESSION_DETAIL, sessionId));
        if (session == null) {
            return;
        }
        if (StringUtils.hasText(session.getRefreshToken())) {
            stringRedisTemplate.delete(String.format(AUTH_REFRESH_TOKEN, session.getRefreshToken()));
        }
        if (StringUtils.hasText(session.getUserId())) {
            stringRedisTemplate.opsForZSet().remove(String.format(AUTH_USER_SESSION_ZSET, session.getUserId()), sessionId);
        }
    }

    /**
     * 撤销用户全部会话（全端下线）
     *
     * @param userId 用户ID
     * @return 被撤销的会话数量
     */
    @Override
    public int revokeAllSessions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        String zsetKey = String.format(AUTH_USER_SESSION_ZSET, userId);
        Set<String> sessionIds = stringRedisTemplate.opsForZSet().range(zsetKey, 0, -1);
        int revoked = 0;
        if (sessionIds != null) {
            for (String sessionId : new LinkedHashSet<>(sessionIds)) {
                revokeSession(sessionId);
                revoked++;
            }
        }
        stringRedisTemplate.delete(zsetKey);
        log.info("【会话管理】已撤销用户全部会话 userId={}, count={}", userId, revoked);
        return revoked;
    }

    /**
     * 将 Access Token 加入黑名单
     *
     * @param sessionId        会话ID（JWT 的 jti）
     * @param remainingSeconds 剩余有效期（秒）
     * @param reason           撤销原因
     */
    @Override
    public void blacklistAccessToken(String sessionId, long remainingSeconds, String reason) {
        if (!StringUtils.hasText(sessionId) || remainingSeconds <= 0) {
            // 令牌已自然过期，无需写入黑名单
            return;
        }
        stringRedisTemplate.opsForValue().set(String.format(AUTH_ACCESS_BLACKLIST, sessionId),
                StringUtils.hasText(reason) ? reason : "revoked", remainingSeconds, TimeUnit.SECONDS);
    }

    /**
     * 判断 Access Token 是否已被撤销
     *
     * @param sessionId 会话ID（JWT 的 jti）
     * @return true=已撤销
     */
    @Override
    public boolean isBlacklisted(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(String.format(AUTH_ACCESS_BLACKLIST, sessionId)));
    }

    // ==================== 内部工具 ====================

    /**
     * 写入用户会话索引并裁剪超限会话
     *
     * @param userId     用户ID
     * @param sessionId  新会话ID
     * @param loginTs    登录时间戳（毫秒）
     * @param ttlSeconds 索引存活时长（秒）
     * @return 被踢出的会话ID列表
     */
    @SuppressWarnings("unchecked")
    private List<String> addSessionWithLimit(String userId, String sessionId, long loginTs, long ttlSeconds) {
        List<Object> evicted = stringRedisTemplate.execute(sessionLimitScript,
                Collections.singletonList(String.format(AUTH_USER_SESSION_ZSET, userId)),
                sessionId,
                String.valueOf(loginTs),
                String.valueOf(authProperties.getLogin().getMaxSessions()),
                String.valueOf(ttlSeconds));
        if (evicted == null || evicted.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> sessionIds = new ArrayList<>(evicted.size());
        for (Object item : evicted) {
            sessionIds.add(String.valueOf(item));
        }
        return sessionIds;
    }
}
