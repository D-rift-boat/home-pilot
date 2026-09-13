package com.dboat.user.service.impl;

import com.dboat.user.common.constants.AuthLuaConstants;
import com.dboat.user.config.properties.AuthProperties;
import com.dboat.user.service.LoginAttemptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_LOGIN_FAIL;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_LOGIN_LOCK;

/**
 * 登录失败防护服务实现
 *
 * @author dboat
 */
@Slf4j
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** Lua 脚本：失败计数 +1 并按需写入锁定标记 */
    private final DefaultRedisScript<Long> loginFailIncrScript =
            new DefaultRedisScript<>(AuthLuaConstants.LUA_LOGIN_FAIL_INCR, Long.class);

    /** Redis 操作模板 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /**
     * 构建失败计数维度 Key
     *
     * @param orgId      租户ID，可为空
     * @param identifier 登录标识
     * @return 维度 Key
     */
    @Override
    public String buildAttemptKey(String orgId, String identifier) {
        return StringUtils.hasText(orgId) ? orgId + ":" + identifier : identifier;
    }

    /**
     * 判断账号是否处于锁定状态
     *
     * @param attemptKey 维度 Key
     * @return true=已锁定
     */
    @Override
    public boolean isLocked(String attemptKey) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(String.format(AUTH_LOGIN_LOCK, attemptKey)));
    }

    /**
     * 获取锁定剩余秒数
     *
     * @param attemptKey 维度 Key
     * @return 剩余秒数，未锁定时返回 0
     */
    @Override
    public long lockRemainSeconds(String attemptKey) {
        Long ttl = stringRedisTemplate.getExpire(String.format(AUTH_LOGIN_LOCK, attemptKey), TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0L : ttl;
    }

    /**
     * 记录一次登录失败，必要时触发锁定
     *
     * @param attemptKey 维度 Key
     * @return true=本次失败已触发账号锁定
     */
    @Override
    public boolean recordFailure(String attemptKey) {
        AuthProperties.Login login = authProperties.getLogin();
        long lockSeconds = login.getLockMinutes() * 60L;
        long lockUntilTs = System.currentTimeMillis() + lockSeconds * 1000L;

        Long result = stringRedisTemplate.execute(loginFailIncrScript,
                Arrays.asList(String.format(AUTH_LOGIN_FAIL, attemptKey), String.format(AUTH_LOGIN_LOCK, attemptKey)),
                String.valueOf(login.getMaxFailCount()),
                String.valueOf(lockSeconds),
                // 失败计数窗口与锁定时长保持一致，窗口内累计达阈值即锁定
                String.valueOf(lockSeconds),
                String.valueOf(lockUntilTs));

        // 返回负数表示已达阈值并写入锁定标记
        boolean locked = result != null && result < 0;
        if (locked) {
            log.warn("【登录安全】账号连续登录失败已达 {} 次，锁定 {} 分钟 attemptKey={}",
                    login.getMaxFailCount(), login.getLockMinutes(), attemptKey);
        }
        return locked;
    }

    /**
     * 获取当前连续失败次数
     *
     * @param attemptKey 维度 Key
     * @return 失败次数
     */
    @Override
    public int currentFailCount(String attemptKey) {
        String value = stringRedisTemplate.opsForValue().get(String.format(AUTH_LOGIN_FAIL, attemptKey));
        return StringUtils.hasText(value) ? Integer.parseInt(value) : 0;
    }

    /**
     * 清除失败计数与锁定标记
     *
     * @param attemptKey 维度 Key
     */
    @Override
    public void clear(String attemptKey) {
        stringRedisTemplate.delete(Arrays.asList(
                String.format(AUTH_LOGIN_FAIL, attemptKey),
                String.format(AUTH_LOGIN_LOCK, attemptKey)));
    }
}
