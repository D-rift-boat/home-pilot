package com.dboat.iot.utils;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 设备实时状态存储（Redis State Store）—— 按设计文档新 Key 规范重构
 * <p>
 * Redis Key 设计：
 * <ul>
 *   <li>{userId}:{deviceId}:latest → 设备最新完整数据 JSON，TTL 24h（长时间无上报自动过期，判定离线）</li>
 *   <li>{userId}:deviceOnlineCount → 在线设备总数，Redis String 原子计数（INCR/DECR）</li>
 * </ul>
 * </p>
 * <p>
 * 在线数维护策略：
 * <ul>
 *   <li>设备首次正常上报 / 重新上线：SET key（新 Key 创建时 INCR）</li>
 *   <li>设备离线（LWT 触发 / Key 过期）：DEL key + DECR</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Component
public class DeviceStateStore {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateStore.class);

    /** 默认用户ID（前端写死 admin） */
    private static final String DEFAULT_USER_ID = "admin";

    /** 设备最新数据 Key 后缀 */
    private static final String LATEST_SUFFIX = ":latest";

    /** 在线设备总数 Key */
    private static final String ONLINE_COUNT_SUFFIX = ":deviceOnlineCount";

    /** 设备最新数据 TTL：24小时（长时间无上报自动过期，判定设备离线） */
    private static final long LATEST_DATA_TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;

    public DeviceStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== Key 构建 ====================

    /** 构建设备最新数据 Key：{userId}:{deviceId}:latest */
    private String buildLatestKey(String userId, String deviceId) {
        return userId + ":" + deviceId + LATEST_SUFFIX;
    }

    /** 构建设备最新数据 Key（使用默认 userId=admin） */
    private String buildLatestKey(String deviceId) {
        return buildLatestKey(DEFAULT_USER_ID, deviceId);
    }

    /** 构建在线设备总数 Key：{userId}:deviceOnlineCount */
    private String buildOnlineCountKey(String userId) {
        return userId + ONLINE_COUNT_SUFFIX;
    }

    /** 构建在线设备总数 Key（使用默认 userId=admin） */
    private String buildOnlineCountKey() {
        return buildOnlineCountKey(DEFAULT_USER_ID);
    }

    // ==================== 设备最新数据操作 ====================

    /**
     * 更新设备最新数据（SET + TTL 24h）
     * <p>
     * 若 Key 不存在（首次上报 / 过期后重新上报），自动 INCR 在线设备总数。
     * 若 Key 已存在，仅更新数据，不改变在线计数。
     * </p>
     *
     * @param deviceId 设备ID
     * @param json     完整设备数据 JSON 字符串
     * @return true=新设备上线（Key 新建），false=已有设备数据刷新
     */
    public boolean updateDeviceLatestData(String deviceId, String json) {
        String key = buildLatestKey(deviceId);
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, json, LATEST_DATA_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.TRUE.equals(isNew)) {
            // 新设备首次上报，在线数 +1
            incrementOnlineCount();
            log.info("New device online, INCR online count: {}", deviceId);
            return true;
        } else {
            // Key 已存在，仅刷新数据和 TTL
            redisTemplate.opsForValue().set(key, json, LATEST_DATA_TTL_HOURS, TimeUnit.HOURS);
            log.debug("Refreshed device latest data: {}", deviceId);
            return false;
        }
    }

    /**
     * 获取设备最新数据 JSON 字符串
     *
     * @param deviceId 设备ID
     * @return JSON 字符串，Key 不存在时返回 null
     */
    public String getDeviceLatestData(String deviceId) {
        return redisTemplate.opsForValue().get(buildLatestKey(deviceId));
    }

    /**
     * 获取设备最新数据并解析为 JSONObject
     *
     * @param deviceId 设备ID
     * @return JSONObject，Key 不存在或解析失败时返回 null
     */
    public JSONObject getDeviceLatestDataAsJson(String deviceId) {
        String json = getDeviceLatestData(deviceId);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSONObject.parseObject(json);
        } catch (Exception e) {
            log.warn("Failed to parse device latest data for [{}]: {}", deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 设备离线：删除最新数据 Key + DECR 在线数
     * <p>
     * 仅在 Key 确实存在时执行删除和计数递减，避免重复 DECR。
     * </p>
     *
     * @param deviceId 设备ID
     * @return true=成功删除（设备确实离线），false=Key 已不存在
     */
    public boolean setDeviceOffline(String deviceId) {
        String key = buildLatestKey(deviceId);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            decrementOnlineCount();
            log.info("Device offline, DECR online count: {}", deviceId);
            return true;
        }
        log.debug("Device already offline (key not found): {}", deviceId);
        return false;
    }

    /**
     * 删除设备状态（设备删除时调用）
     *
     * @param deviceId 设备ID
     */
    public void removeDeviceState(String deviceId) {
        String key = buildLatestKey(deviceId);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            decrementOnlineCount();
        }
        log.info("Removed device state from Redis: {}", deviceId);
    }

    // ==================== 在线设备数操作 ====================

    /**
     * 在线设备数 +1（原子操作）
     */
    public void incrementOnlineCount() {
        redisTemplate.opsForValue().increment(buildOnlineCountKey());
    }

    /**
     * 在线设备数 -1（原子操作，保证不低于 0）
     */
    public void decrementOnlineCount() {
        String countKey = buildOnlineCountKey();
        // 先获取当前值，大于 0 才递减，防止负数
        String countStr = redisTemplate.opsForValue().get(countKey);
        if (countStr != null && Long.parseLong(countStr) > 0) {
            redisTemplate.opsForValue().decrement(countKey);
        }
    }

    /**
     * 获取在线设备总数
     *
     * @return 在线设备数，Key 不存在时返回 0
     */
    public long getOnlineCount() {
        String countStr = redisTemplate.opsForValue().get(buildOnlineCountKey());
        if (countStr == null || countStr.isEmpty()) {
            return 0;
        }
        long count = Long.parseLong(countStr);
        return Math.max(count, 0);
    }

    // ==================== 在线设备扫描 ====================

    /**
     * 获取所有在线设备ID列表
     * <p>
     * 通过 Redis SCAN 命令扫描 {userId}:*:latest 模式的 Key，
     * 提取 deviceId 部分。使用 SCAN 而非 KEYS，避免阻塞 Redis。
     * </p>
     *
     * @return 在线设备ID集合
     */
    public Set<String> getOnlineDeviceIds() {
        Set<String> onlineIds = new HashSet<>();
        String pattern = DEFAULT_USER_ID + ":*:" + LATEST_SUFFIX.substring(1); // admin:*:latest

        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(200)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // 从 Key 中提取 deviceId：admin:{deviceId}:latest → {deviceId}
                String deviceId = extractDeviceIdFromKey(key);
                if (deviceId != null) {
                    onlineIds.add(deviceId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan online device keys: {}", e.getMessage());
        }
        return onlineIds;
    }

    /**
     * 从 Redis Key 中提取 deviceId
     * <p>
     * Key 格式：admin:{deviceId}:latest → 提取中间部分
     * </p>
     *
     * @param key Redis Key
     * @return deviceId，格式不匹配时返回 null
     */
    private String extractDeviceIdFromKey(String key) {
        String prefix = DEFAULT_USER_ID + ":";
        if (!key.startsWith(prefix) || !key.endsWith(LATEST_SUFFIX)) {
            return null;
        }
        return key.substring(prefix.length(), key.length() - LATEST_SUFFIX.length());
    }
}
