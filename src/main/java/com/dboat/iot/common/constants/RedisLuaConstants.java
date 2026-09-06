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
}
