package com.dboat.iot.common.constants;

/**
 * Redis 相关常量：Key 前缀、Lua 脚本、TTL 等
 *
 * @author dboat
 */
public final class RedisConstants {

    private RedisConstants() {
    }

    // ==================== Redis Key 前缀 ====================

    /**
     * 设备最新数据 Key 前缀：ws:iot_device:latest:{deviceId}
     */
    public static final String DEVICE_LATEST_PREFIX = "ws:iot_device:latest:";

    /**
     * 用户在线设备总数 Key 前缀：ws:stat:online_user_device_count:{userId}
     */
    public static final String USER_ONLINE_COUNT_PREFIX = "ws:stat:online_user_device_count:";

    /**
     * 在线 IoT 设备总数 Key 前缀：ws:stat:online_iot_device_count:{userId}
     */
    public static final String IOT_ONLINE_COUNT_PREFIX = "ws:stat:online_iot_device_count:";

    // ==================== TTL 常量 ====================

    /**
     * 设备最新数据 TTL：24小时（长时间无上报自动过期，判定设备离线）
     */
    public static final long DEVICE_LATEST_DATA_TTL_HOURS = 24;

    /**
     * IoT 设备在线列表 TTL：86400秒（24小时），与 Lua 脚本中的 EXPIRE 保持一致
     */
    public static final long IOT_DEVICE_ONLINE_TTL_SECONDS = 86400;

    // ==================== Lua 脚本 ====================

    /**
     * Lua 脚本：仅当值为正数时递减（防止计数器减到负数）
     * <p>KEYS[1] = 计数器 Key</p>
     */
    public static final String LUA_DECR_IF_POSITIVE =
            "local val = tonumber(redis.call('get', KEYS[1])) " +
                    "if val and val > 0 then " +
                    "    return redis.call('decr', KEYS[1]) " +
                    "end " +
                    "return 0";

    /**
     * Lua 脚本：IoT 设备上线（原子写入 + 设置 TTL + 返回在线数）
     * <p>KEYS[1] = 用户在线设备 Hash Key，ARGV[1] = deviceId，ARGV[2] = IotDevLineDTO JSON</p>
     */
    public static final String LUA_IOT_ONLINE = """
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[1], 86400)
            return redis.call('HLEN', KEYS[1])
            """;

    /**
     * Lua 脚本：IoT 设备离线（原子删除 + 返回剩余在线数）
     * <p>KEYS[1] = 用户在线设备 Hash Key，ARGV[1] = deviceId</p>
     */
    public static final String LUA_IOT_OFFLINE = """
            redis.call('HDEL', KEYS[1], ARGV[1])
            return redis.call('HLEN', KEYS[1])
            """;

    /**
     * Lua 脚本：原子化 Hash 批量写入 + 设置整体过期时间
     * <p>
     * ARGV 约定：ARGV[1] = TTL秒数，ARGV[2..N] = field-value 对。
     * 分离 TTL 参数，避免 unpack 展开时混入 HSET 导致参数个数为奇数。
     * </p>
     * <p>KEYS[1] = Hash Key</p>
     */
    public static final String LUA_HASH_PUT_ALL_WITH_EXPIRE = """
            redis.call('HSET', KEYS[1], unpack(ARGV, 2, #ARGV))
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            return 1
            """;
}
