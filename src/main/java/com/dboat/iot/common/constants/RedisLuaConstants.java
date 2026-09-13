package com.dboat.iot.common.constants;

/**
 * Redis 相关常量：Key 前缀、Lua 脚本、TTL 等
 *
 * @author dboat
 */
public final class RedisLuaConstants {
    /**
     *
     * --[[
     * KEYS[1] 用户会话zset key
     * KEYS[2] 用户会话hash key
     * return 1：zset为空，已删除hash；0：zset还有数据，不操作
     * ]]
     */
    public static final String CHECK_ZSET_EMPTY_ONLY_DEL_HASH_LUA = """
        local zCardVal = redis.call('ZCARD', KEYS[1])
        if zCardVal == 0 then
            redis.call('DEL', KEYS[2])
        end
        return zCardVal
        """;

    /**
     *
     * --[[
     * KEYS[1] 用户会话zset key
     * ARGV[1] 用户会话id
     * ARGV[2] 阈值
     * ]]
     */
    public static final String ZREM_IF_MEM_EX_LUA = """
            local gKey = KEYS[1]
            local uid = ARGV[1]
            local threshold = tonumber(ARGV[2])
            local score = redis.call('ZSCORE', gKey, uid)
            if not score then
                return 0
            end
            score = tonumber(score)
            if score < threshold then
                redis.call('ZREM', gKey, uid)
                return 1
            else
                return 0
            end
            """;

    /**
     *
     * --[[
     * KEYS[1] 用户会话zset key
     * KEYS[2] 用户会话hash key
     * return 1：zset为空，已删除hash；0：zset还有数据，不操作
     * ]]
     */
    public static final String CHECK_ZSET_EMPTY_DEL_ZSET_LUA = """
        local zCardVal = redis.call('ZCARD', KEYS[1])
        if zCardVal == 0 then
            redis.call('DEL', KEYS[1])
        end
        return zCardVal
        """;

    /**
     *
     * --[[
     * 查出user过期的用户会话 并返回set,删除 过期的用户会话
     * KEYS[1] 用户会话zset key
     * ARGV[1] 过期时间阈值
     * return 过期的用户会话set、剩余数量
     * ]]
     */
    public static final String CLEAN_EX_USER_CONN_LUA = """
            local key = KEYS[1]
            local threshold = tonumber(ARGV[1])
            local expiredMembers = redis.call('ZRANGEBYSCORE', key, 0, threshold)
            local delCount = 0
            if #expiredMembers > 0 then
                delCount = redis.call('ZREM', key, unpack(expiredMembers))
            end
            local remain = redis.call('ZCARD', key)
            return {expiredMembers, remain}
            """;

    // ZADD + EXPIRE 单key通用lua
    public static final String LUA_ZADD_WITH_EXPIRE = """
        redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
        redis.call('EXPIRE', KEYS[1], ARGV[3])
        return 1
        """;

    // HSET + EXPIRE 单key通用lua
    public static final String LUA_HSET_WITH_EXPIRE = """
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        redis.call('EXPIRE', KEYS[1], ARGV[3])
        return 1
        """;

    /**
     * String SET + EX 原子脚本，对标LUA_HSET_WITH_EXPIRE
     */
    public static final String LUA_STRING_SET_WITH_EXPIRE = """
        redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
        return 1
        """;

    /**
     * LUA_REMOVE_SESSION: remove session hash field + zset member, clean up metadata when user zset becomes empty
     * KEYS[2] = userConnZsetKey (ws:on:user:ses:{userId})
     * ARGV[1] = sessionId
     */
    public static final String LUA_REMOVE_SESSION = """
        redis.call('HDEL', KEYS[1], ARGV[1])
        redis.call('ZREM', KEYS[2], ARGV[1])
        local remain = redis.call('ZCARD', KEYS[2])
        if remain == 0 then
            redis.call('DEL', KEYS[2])
            redis.call('ZREM', KEYS[2], ARGV[1])
        end
        return remain
        """;

    /**
     * LUA_HASH_CLEAN_EXPIRED_SESSION
     * Iterate hash, parse json actiTs, delete expired session field atomically
     * KEYS[1]: user hash key(with hash‑tag)
     * ARGV[1]: now timestamp ms
     * return array: [deletedCount, remainCount]
     */
    public static final String LUA_HASH_CLEAN_EXPIRED_SESSION = """
        local hashKey = KEYS[1]
        local nowMs = tonumber(ARGV[1])
        
        local all = redis.call('HGETALL', hashKey)
        local delCount = 0
        local remainCount = 0
        
        for i=1,#all,2 do
            local field = all[i]
            local jsonStr = all[i+1]
            local ok, obj = pcall(cjson.decode, jsonStr)
            if ok and type(obj) == 'table' and obj.actiTs then
                local actiTs = tonumber(obj.actiTs)
                if actiTs < nowMs then
                    redis.call('HDEL', hashKey, field)
                    delCount = delCount + 1
                else
                    remainCount = remainCount +1
                end
            else
                redis.call('HDEL', hashKey, field)
                delCount = delCount +1
            end
        end
        
        if remainCount == 0 then
            redis.call('DEL',hashKey)
        end
        
        return {delCount, remainCount}
        """;

    //===========================  iot device manage lua  ==============================
    /**
     * LUA_IOT_DEV_HEARTBEAT
     * KEYS[1]: iot device heartbeat key
     * ARGV[1]: heartbeat timestamp
     * ARGV[2]: expire time   s
     * return 1: updated; 0: not updated
     */
    public static final String LUA_IOT_DEV_HEARTBEAT = """
            local oldTs = redis.call('GET', KEYS[1])
            if oldTs and tonumber(ARGV[1]) <= tonumber(oldTs) then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """;
}
