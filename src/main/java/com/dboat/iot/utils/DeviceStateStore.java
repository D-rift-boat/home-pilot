package com.dboat.iot.utils;

import com.dboat.iot.enums.DeviceOnlineStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 设备实时状态存储（Redis State Store）
 * <p>
 * Hash 结构：Key = iot:device:state:{device_id}
 * Fields:
 *   - online_status : 在线状态码（0=离线,1=在线,2=异常）
 *   - last_seen     : 最后活跃时间戳（毫秒）
 *   - sensor_status : 传感器整体状态码
 *   - aht20_status  : AHT20 传感器状态码
 *   - bmp280_status : BMP280 传感器状态码
 */
@Component
public class DeviceStateStore {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateStore.class);

    private static final String KEY_PREFIX = "iot:device:state:";
    private static final long KEY_EXPIRE_HOURS = 48;

    private static final String FIELD_ONLINE_STATUS = "online_status";
    private static final String FIELD_LAST_SEEN = "last_seen";
    private static final String FIELD_SENSOR_STATUS = "sensor_status";
    private static final String FIELD_AHT20_STATUS = "aht20_status";
    private static final String FIELD_BMP280_STATUS = "bmp280_status";

    private final StringRedisTemplate redisTemplate;

    public DeviceStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String buildKey(String deviceId) {
        return KEY_PREFIX + deviceId;
    }

    /**
     * 刷新设备在线状态及最新传感器状态
     */
    public void refreshDeviceState(String deviceId, int sensorStatus, int aht20Status, int bmp280Status) {
        String key = buildKey(deviceId);
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_ONLINE_STATUS, String.valueOf(DeviceOnlineStatusEnum.ONLINE.getCode()));
        fields.put(FIELD_LAST_SEEN, String.valueOf(Instant.now().toEpochMilli()));
        fields.put(FIELD_SENSOR_STATUS, String.valueOf(sensorStatus));
        fields.put(FIELD_AHT20_STATUS, String.valueOf(aht20Status));
        fields.put(FIELD_BMP280_STATUS, String.valueOf(bmp280Status));

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, KEY_EXPIRE_HOURS, TimeUnit.HOURS);
        log.debug("Refreshed device state in Redis for device: {}", deviceId);
    }

    /**
     * 更新设备在线状态为离线
     */
    public void setDeviceOffline(String deviceId) {
        String key = buildKey(deviceId);
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_ONLINE_STATUS, String.valueOf(DeviceOnlineStatusEnum.OFFLINE.getCode()));
        fields.put(FIELD_LAST_SEEN, String.valueOf(Instant.now().toEpochMilli()));

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, KEY_EXPIRE_HOURS, TimeUnit.HOURS);
        log.info("Set device OFFLINE in Redis: {}", deviceId);
    }

    /**
     * 获取设备指定字段值
     */
    public String getField(String deviceId, String field) {
        String key = buildKey(deviceId);
        Object value = redisTemplate.opsForHash().get(key, field);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取设备全部状态字段
     */
    public Map<String, String> getDeviceState(String deviceId) {
        String key = buildKey(deviceId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<String, String> result = new HashMap<>();
        entries.forEach((k, v) -> result.put(k.toString(), v.toString()));
        return result;
    }

    /**
     * 获取设备在线状态
     */
    public DeviceOnlineStatusEnum getOnlineStatus(String deviceId) {
        String statusStr = getField(deviceId, FIELD_ONLINE_STATUS);
        if (statusStr == null) {
            return DeviceOnlineStatusEnum.OFFLINE;
        }
        return DeviceOnlineStatusEnum.fromCode(Integer.parseInt(statusStr));
    }

    /**
     * 删除设备状态（设备删除时调用）
     */
    public void removeDeviceState(String deviceId) {
        redisTemplate.delete(buildKey(deviceId));
        log.info("Removed device state from Redis: {}", deviceId);
    }
}
