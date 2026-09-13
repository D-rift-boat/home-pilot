package com.dboat.user.common.constants;

/**
 * 认证鉴权模块 Redis Lua 脚本常量
 * <p>
 * 所有涉及"读-判断-写"多步操作的场景统一使用 Lua 脚本保证原子性，
 * 避免并发下的令牌重放、会话数超限、验证码重复使用等竞态问题。
 * 脚本统一通过 {@code StringRedisTemplate#execute(DefaultRedisScript, ...)} 执行。
 * </p>
 *
 * @author dboat
 */
public final class AuthLuaConstants {

    private AuthLuaConstants() {
    }

    /**
     * Refresh Token 原子消费 + 重放检测
     * <p>
     * 轮换策略核心：每次刷新将旧 Refresh Token 标记为 used=1 并保留剩余 TTL，
     * 一旦检测到已消费的旧令牌被再次使用，即判定令牌泄漏，返回重放标记由业务侧撤销该用户全部会话。
     * </p>
     * <pre>
     * KEYS[1] = auth:refresh:token:{oldRefreshToken}
     * return  nil                                        → 令牌不存在或已过期
     *         {"replay":1,"userId":...,"orgId":...}      → 重放攻击，需撤销全部会话
     *         {"replay":0,"data":"原始会话JSON"}          → 消费成功，可签发新令牌
     * </pre>
     */
    public static final String LUA_REFRESH_CONSUME = """
            local raw = redis.call('GET', KEYS[1])
            if not raw then
                return nil
            end
            local ok, obj = pcall(cjson.decode, raw)
            if not ok or type(obj) ~= 'table' then
                redis.call('DEL', KEYS[1])
                return nil
            end
            if obj.used == 1 then
                return cjson.encode({ replay = 1, userId = obj.userId, orgId = obj.orgId, sessionId = obj.sessionId })
            end
            obj.used = 1
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 60 then
                ttl = 60
            end
            redis.call('SET', KEYS[1], cjson.encode(obj), 'EX', ttl)
            return cjson.encode({ replay = 0, data = raw })
            """;

    /**
     * 登录失败计数 +1，达到阈值时原子写入账号锁定标记
     * <pre>
     * KEYS[1] = auth:login:fail:{orgId:identifier}
     * KEYS[2] = auth:login:lock:{orgId:identifier}
     * ARGV[1] = 最大失败次数
     * ARGV[2] = 锁定时长（秒）
     * ARGV[3] = 失败计数 Key 存活时长（秒）
     * ARGV[4] = 锁定截止时间戳（ms）
     * return  正数 = 当前失败次数；负数 = 已触发锁定（绝对值为失败次数）
     * </pre>
     */
    public static final String LUA_LOGIN_FAIL_INCR = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[3])
            end
            if count >= tonumber(ARGV[1]) then
                redis.call('SET', KEYS[2], ARGV[4], 'EX', ARGV[2])
                redis.call('DEL', KEYS[1])
                return -count
            end
            return count
            """;

    /**
     * 固定窗口限流计数（INCR + 首次设置 EXPIRE）
     * <pre>
     * KEYS[1] = auth:limit:{uri}:{ip}
     * ARGV[1] = 窗口时长（秒）
     * return  当前窗口内的累计请求数
     * </pre>
     */
    public static final String LUA_RATE_LIMIT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """;

    /**
     * 验证码校验（一次性，校验通过即删除，防重放）
     * <pre>
     * KEYS[1] = auth:captcha:{scene}:{identifier}
     * ARGV[1] = 用户提交的验证码
     * return  1 = 校验通过；0 = 验证码不存在/已过期/不匹配
     * </pre>
     */
    public static final String LUA_VERIFY_CAPTCHA = """
            local saved = redis.call('GET', KEYS[1])
            if not saved then
                return 0
            end
            if saved ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """;

    /**
     * 新增会话并裁剪超限会话（并发会话控制）
     * <p>
     * 单用户会话数超过上限时，按登录时间戳升序踢掉最早的会话，
     * 返回被踢出的 sessionId 列表，业务侧据此清理会话详情与对应 Refresh Token。
     * </p>
     * <pre>
     * KEYS[1] = auth:user:sessions:{userId}
     * ARGV[1] = 新会话 sessionId
     * ARGV[2] = 登录时间戳（ms）
     * ARGV[3] = 最大并发会话数
     * ARGV[4] = ZSet 存活时长（秒）
     * return  被踢出的 sessionId 列表（无超限则为空列表）
     * </pre>
     */
    public static final String LUA_SESSION_ADD_WITH_LIMIT = """
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            local total = redis.call('ZCARD', KEYS[1])
            local max = tonumber(ARGV[3])
            if total <= max then
                return {}
            end
            local overflow = total - max
            local evicted = redis.call('ZRANGE', KEYS[1], 0, overflow - 1)
            if #evicted > 0 then
                redis.call('ZREM', KEYS[1], unpack(evicted))
            end
            return evicted
            """;
}
