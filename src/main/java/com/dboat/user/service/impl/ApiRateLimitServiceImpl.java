package com.dboat.user.service.impl;

import com.dboat.user.common.constants.AuthLuaConstants;
import com.dboat.user.config.properties.AuthProperties;
import com.dboat.user.service.ApiRateLimitService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_RATE_LIMIT;

/**
 * 认证接口限流服务实现（Redis + Lua 固定窗口计数）
 * <p>
 * 容错策略：Redis 异常时采取「失败放行」（fail-open），仅记录告警。
 * 限流属于旁路防护，不应因缓存抖动导致全部用户无法登录；
 * 真正的暴力破解防线由 {@link LoginAttemptServiceImpl} 的账号锁定兜底。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Service
public class ApiRateLimitServiceImpl implements ApiRateLimitService {

    /** Lua 脚本：固定窗口 INCR + 首次 EXPIRE */
    private final DefaultRedisScript<Long> rateLimitScript =
            new DefaultRedisScript<>(AuthLuaConstants.LUA_RATE_LIMIT, Long.class);

    /** Redis 操作模板 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /**
     * 尝试获取一次请求配额
     *
     * @param uri      请求 URI
     * @param clientIp 客户端 IP
     * @return true=放行
     */
    @Override
    public boolean tryAcquire(String uri, String clientIp) {
        AuthProperties.RateLimit rateLimit = authProperties.getRateLimit();
        if (!rateLimit.isEnabled()) {
            return true;
        }
        try {
            Long count = stringRedisTemplate.execute(rateLimitScript,
                    Collections.singletonList(buildKey(uri, clientIp)),
                    String.valueOf(rateLimit.getWindowSeconds()));
            if (count == null) {
                return true;
            }
            if (count > rateLimit.getMaxRequests()) {
                log.warn("【接口限流】触发限流 uri={}, ip={}, count={}/{}（窗口 {}s）",
                        uri, clientIp, count, rateLimit.getMaxRequests(), rateLimit.getWindowSeconds());
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 异常时放行，避免限流组件故障演变为全站登录不可用
            log.warn("【接口限流】Redis 计数异常，本次放行 uri={}, ip={}, cause={}", uri, clientIp, e.getMessage());
            return true;
        }
    }

    /**
     * 获取当前限流窗口的剩余秒数
     *
     * @param uri      请求 URI
     * @param clientIp 客户端 IP
     * @return 剩余秒数
     */
    @Override
    public long windowRemainSeconds(String uri, String clientIp) {
        try {
            Long ttl = stringRedisTemplate.getExpire(buildKey(uri, clientIp), TimeUnit.SECONDS);
            return ttl == null || ttl < 0 ? 0L : ttl;
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 构建限流 Key
     *
     * @param uri      请求 URI
     * @param clientIp 客户端 IP
     * @return 限流 Key
     */
    private String buildKey(String uri, String clientIp) {
        return String.format(AUTH_RATE_LIMIT, uri, clientIp);
    }
}
