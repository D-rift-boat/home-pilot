package com.dboat.iot.service.ws;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.common.constants.MqttConstants;
import com.dboat.iot.dto.mqtt.MqttMessageHeader;
import com.dboat.iot.dto.ws.IotDevLineDTO;
import com.dboat.iot.dto.ws.WsUploadDataDTO;
import com.dboat.iot.service.UserDeviceRelService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dboat.iot.common.constants.MqttConstants.IOT_DEV_SHADOW;
import static com.dboat.iot.common.constants.RedisConstants.*;

/**
 * 设备实时状态服务（Redis State Service）—— 按设计文档新 Key 规范重构
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
public class DeviceStateService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateService.class);

    /**
     * Redis 模板，用于操作 Redis
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 用户-设备关系服务，用于查询设备订阅者列表（Redis 优先 + DB 回源）
     */
    @Lazy
    @Resource
    private UserDeviceRelService userDeviceRelService;

    /**
     * Redisson 客户端，用于执行 Lua 脚本
     */
    @Resource
    private RedissonClient redissonClient;

    /**
     * Redisson 脚本执行器，用于执行 Lua 脚本
     */
    @Resource
    private RScript scriptExecutor;

    /**
     * Lua 脚本：设备心跳处理
     */
    @Resource
    private DefaultRedisScript<Integer> iotDevHeartbeatScript;

    /**
     * Lua 脚本：仅当值为正数时递减（防止计数器减到负数）
     */
    private final DefaultRedisScript<Long> decrScript =
            new DefaultRedisScript<>(LUA_DECR_IF_POSITIVE, Long.class);


    // ==================== Key 构建 ====================

    /**
     * 构建设备最新数据 Key：ws:iot_device:latest:{deviceId}
     */
    private String buildLatestKey(String deviceId) {
        return DEVICE_LATEST_PREFIX + deviceId;
    }

    // ==================== 设备最新数据操作 ====================

    /**
     * 更新设备最新数据（SET + TTL 24h）
     * <p>
     * 若 Key 不存在（首次上报 / 过期后重新上报），自动 INCR 在线设备总数。
     * 若 Key 已存在，仅更新数据，不改变在线计数。
     * </p>
     *
     * @param deviceId      设备ID
     * @param deviceDataMap 完整设备数据 Map
     * @return true=新设备上线（Key 新建），false=已有设备数据刷新
     */
    public boolean updateDeviceLatestData(String deviceId, Map deviceDataMap) {
        // 更新设备最新数据快照
        String key = buildLatestKey(deviceId);
        stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(deviceDataMap), DEVICE_LATEST_DATA_TTL_HOURS, TimeUnit.HOURS);

        // iot设备心跳处理
        // 获取所有订阅该设备的用户列表
        Set<String> userIdSet = userDeviceRelService.getSubscriberUserIds(deviceId);
        // 更新所属用户的在线iot设备列表
        for (String userId : userIdSet) {
            // 构建 IotDevLineDTO JSON 作为 Hash value（与 onlineUserIotDev/offlineUserIotDev 格式一致）
            // Hash 结构：field=deviceId, value=IotDevLineDTO JSON
            IotDevLineDTO devLine = IotDevLineDTO.builder()
                    .deviceId(deviceId)
                    .lastReportTs(deviceDataMap.get("timestamp").toString())
                    .build();
            Map<String, String> iotOnlineMap = new HashMap<>();
            iotOnlineMap.put(deviceId, JSONObject.toJSONString(devLine));

            String iotOnlineKey = String.format(IOT_DEV_SHADOW, userId);
            // 原子化：批量写入 + 设置整key过期
            atomicHashPutAll(iotOnlineKey, iotOnlineMap, DEVICE_LATEST_DATA_TTL_HOURS, TimeUnit.HOURS);
        }

        log.debug("Refreshed device latest data: {}", deviceId);
        return true;
    }


    /**
     * 处理iot设备心跳（SET + TTL 90s）
     * <p>
     * 若 Key 不存在（首次上报 / 过期后重新上报），自动 INCR 在线设备总数。
     * 若 Key 已存在，仅更新数据，不改变在线计数。
     * </p>
     *
     * @param header     mqtt 报文 header
     * @return true=新设备上线（Key 新建），false=已有设备数据刷新
     */
    public Boolean dealIotHeartBeat(MqttMessageHeader header) {
        String deviceId = header.getDeviceId();
        // ========== 刷新 Redis 设备心跳记录  ex 90s
        Integer executeRes = stringRedisTemplate.execute(iotDevHeartbeatScript,
                Collections.singletonList(String.format(MqttConstants.IOT_DEV_ACTIVE, header.getDeviceId())),
                header.getTimestamp().toString(),
                String.valueOf(MqttConstants.IOT_DEV_ACTIVE_EX));

        log.debug("Processed UP_DATA from device: {}", deviceId);

        return ObjectUtils.isNotEmpty(executeRes) && executeRes > 0;
    }

    /**
     * Hash 批量写入并原子设置整体过期
     * <p>
     * Lua ARGV 约定：ARGV[1] = TTL秒数，ARGV[2..N] = field-value 对。
     * TTL 作为首个参数传入，与 field-value 对分离，确保 unpack 不会将 TTL 混入 HSET。
     * </p>
     *
     * @param redisKey hash主key
     * @param map      field-value 数据
     * @param ttl      过期时长
     * @param unit     时间单位
     * @return true成功 / false失败
     */
    public boolean atomicHashPutAll(String redisKey, Map<String, String> map, long ttl, TimeUnit unit) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        long ttlSeconds = unit.toSeconds(ttl);

        // ARGV[1] = TTL秒数，ARGV[2..N] = field-value 对
        List<Object> argv = new ArrayList<>();
        argv.add(ttlSeconds);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            argv.add(entry.getKey());
            argv.add(entry.getValue());
        }

        Object res = scriptExecutor.eval(
                RScript.Mode.READ_WRITE,
                LUA_HASH_PUT_ALL_WITH_EXPIRE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(redisKey),
                argv.toArray()
        );
        return Objects.equals(res, 1L);
    }

    /**
     * 获取设备最新数据 JSON 字符串
     *
     * @param deviceId 设备ID
     * @return JSON 字符串，Key 不存在时返回 null
     */
    public String getDeviceLatestData(String deviceId) {
        return stringRedisTemplate.opsForValue().get(buildLatestKey(deviceId));
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
     * 获取 deviceId 所属设备最新数据
     * TODO 后期端设备考虑 flink 取计算后的数据 / 或带上deviceId 进行精确查询
     *
     * @param deviceId 用户ID
     * @return WsUploadDataDTO 不存在或解析失败时返回 null
     */
    public JSONObject getIotDeviceLatestDataByDeviceId(String deviceId) {
        String deviceLatestData = stringRedisTemplate.opsForValue().get(buildLatestKey(deviceId));
        if (ObjectUtils.isNotEmpty(deviceLatestData)) {
            try {
                return JSONObject.parseObject(deviceLatestData);
            } catch (Exception e) {
                // 日志带上key和原始数据，方便线上排查脏数据
                log.error("解析设备最新JSON数据失败，key:{}, rawData:{}", deviceId, deviceLatestData, e);
            }
        }
        return new JSONObject();
    }

    /**
     * 获取用户 userId 所属设备最新数据
     * TODO 后期端设备考虑 flink 取计算后的数据 / 或带上deviceId 进行精确查询
     *
     * @param userId 用户ID
     * @return WsUploadDataDTO 不存在或解析失败时返回 null
     */
    public WsUploadDataDTO getIotDeviceLatestDataByUserId(String userId) {
        // 获取用户订阅设备列表
        List<String> subscribedDeviceIds = userDeviceRelService.getSubscribedDeviceIds(userId);
        if (ObjectUtils.isEmpty(subscribedDeviceIds)) {
            return new WsUploadDataDTO();
        }
        // TODO 暂时只取第一个设备 的最新数据快照
        String deviceLatestData = getDeviceLatestData(subscribedDeviceIds.get(0));
        Map<Object, Object> map = JSONObject.parseObject(deviceLatestData, Map.class);
        if (map == null || map.isEmpty()) {
            return new WsUploadDataDTO();
        }

        WsUploadDataDTO.DataDTO dataDTO = WsUploadDataDTO.DataDTO.builder()
                .tempAht(getStr(map, "tempAht"))
                .tempBmp(getStr(map, "tempBmp"))
                .humidity(getStr(map, "humidity"))
                .pressureHpa(getStr(map, "pressureHpa"))
                .altitude(getStr(map, "altitude"))
                .iotDeviceOnlineCount(getInteger(map, "iotDeviceOnlineCount"))
                .userDeviceOnlineCount(getInteger(map, "userDeviceOnlineCount"))
                .build();

        WsUploadDataDTO.DeviceDTO deviceDTO = WsUploadDataDTO.DeviceDTO.builder()
                .deviceId(getStr(map, "deviceId"))
                .deviceStatus(getInteger(map, "deviceStatus"))
                .aht20Status(getInteger(map, "aht20Status"))
                .bmp280Status(getInteger(map, "bmp280Status"))
                .build();

        // type、timestamp 程序内部生成，不从redis读取
        WsUploadDataDTO wsUploadDataDTO = WsUploadDataDTO.of(dataDTO, deviceDTO);
        wsUploadDataDTO.setTimestamp(getStr(map, "timestamp"));
        return wsUploadDataDTO;
    }

    /**
     * 读取hash字段，获取BigDecimal
     */
    private static BigDecimal getNum(Map<Object, Object> map, String field) {

        Object val = map.get(field);
        if (val == null) {
            return null;
        }
        String raw = val.toString().trim();
        if ("".equals(raw) || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        return new BigDecimal(raw);
    }

    /**
     * 读取hash字段，获取Integer
     */
    private static Integer getInteger(Map<Object, Object> map, String field) {

        Object val = map.get(field);
        if (val == null) {
            return null;
        }
        String raw = val.toString().trim();
        if ("".equals(raw) || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        return Integer.valueOf(new BigDecimal(raw).toString());
    }

    /**
     * 读取hash字段，null/空字符串统一返回null
     */
    private static String getStr(Map<Object, Object> map, String field) {
        Object val = map.get(field);
        if (val == null) {
            return null;
        }
        String raw = val.toString().trim();
        if ("".equals(raw) || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        return raw;
    }

    /**
     * IoT 设备离线：从所有订阅用户的在线列表中移除该设备
     * <p>
     * 纯 Redis 操作，不涉及 WS 事件推送（由调用方负责构建事件并推送）。
     * </p>
     *
     * @param iotDeviceId 离线设备ID
     * @return 最后一个订阅用户更新后的剩余在线设备数（无订阅用户时返回 0）
     */
    public long iotDeviceOffline(String iotDeviceId) {
        Set<String> userIdSet = userDeviceRelService.getSubscriberUserIds(iotDeviceId);
        long lastCount = 0;
        for (String userId : userIdSet) {
            lastCount = offlineUserIotDev(userId, iotDeviceId);
            log.info("IoT device offline, userId={}, remainingCount={}", userId, lastCount);
        }
        return lastCount;
    }

    /**
     * IoT 设备上线：将设备加入所有订阅用户的在线列表
     * <p>
     * 纯 Redis 操作，不涉及 WS 事件推送（由调用方负责构建事件并推送）。
     * </p>
     *
     * @param iotDeviceId 上线设备ID
     * @return 最后一个订阅用户更新后的在线设备数（无订阅用户时返回 0）
     */
    public long iotDeviceOnline(String iotDeviceId) {
        Set<String> userIdSet = userDeviceRelService.getSubscriberUserIds(iotDeviceId);
        log.info("Device subscribe user list：{}", userIdSet);
        long lastCount = 0;
        for (String userId : userIdSet) {
            lastCount = onlineUserIotDev(userId, iotDeviceId);
            log.info("IoT device online, userId={}, onlineCount={}", userId, lastCount);
        }
        return lastCount;
    }

    /**
     * 删除设备状态（设备删除时调用）
     *
     * @param deviceId 设备ID
     */
    public void removeDeviceState(String deviceId) {
        String key = buildLatestKey(deviceId);
        Boolean deleted = stringRedisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            //decrementOnlineIotDevCount(DEFAULT_USER_ID);
        }
        log.info("Removed device state from Redis: {}", deviceId);
    }

    // ==================== 在线设备数操作 ====================

    /**
     * 删除用户在线设备列表中的指定设备
     */
    public Long offlineUserIotDev(String userId, String iotDeviceId) {
        String redisKey = String.format(IOT_DEV_SHADOW, userId);
        IotDevLineDTO iotDevLineDTO = IotDevLineDTO.builder()
                .deviceId(iotDeviceId)
                .lastReportTs(String.valueOf(System.currentTimeMillis()))
                .build();
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long remainCount = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_IOT_OFFLINE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(redisKey),
                iotDeviceId, JSONObject.toJSONString(iotDevLineDTO)
        );
        // 递减后如果为0，可以触发推送"设备全部离线"事件
        if (remainCount == 0) {
            log.info("All iot devices offline for user: {}", userId);
            // pushAllOfflineEvent(userId);
        }

        return remainCount;
    }

    /**
     * 新增/更新用户在线设备列表中的指定设备（原子操作）
     */
    public Long onlineUserIotDev(String userId, String iotDeviceId) {
        String redisKey = String.format(IOT_DEV_SHADOW, userId);
        IotDevLineDTO iotDevLineDTO = IotDevLineDTO.builder()
                .deviceId(iotDeviceId)
                .lastReportTs(String.valueOf(System.currentTimeMillis()))
                .build();
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long remainCount = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_IOT_ONLINE,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(redisKey),
                iotDeviceId, JSONObject.toJSONString(iotDevLineDTO)
        );
        //

        return remainCount;
    }

    /**
     * 获取用户在线iot设备总数
     *
     * @return 在线设备数，Key 不存在时返回 0
     */
    public Long getUserIotDeviceOnlineCount(String userId) {
        String redisKey = String.format(IOT_DEV_SHADOW, userId);

        Long countStr = stringRedisTemplate.opsForHash().size(redisKey);
        if (ObjectUtils.isEmpty(countStr)) {
            countStr = 0L;
        }
        return countStr;
    }

    /**
     * 获取用户在线IoT设备列表
     * <p>
     * 查询 iot:device:online:{userId} Hash，返回 deviceId → IotDevLineDTO JSON 映射。
     * </p>
     *
     * @return 在线设备映射，Key 不存在时返回空 Map
     */
    public Map<String, JSONObject> getUserDeviceOnlineMapList(String userId) {
        String redisKey = String.format(IOT_DEV_SHADOW, userId);

        Map<String, JSONObject> entries = stringRedisTemplate.opsForHash().entries(redisKey).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> JSONObject.parseObject(entry.getValue().toString())
                ));
        if (ObjectUtils.isEmpty(entries)) {
            entries = Collections.emptyMap();
        }
        return entries;
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
    public Set<String> getOnlineDeviceIds(String userId) {
        String redisKey = String.format(IOT_DEV_SHADOW, userId);

        Set<String> members = stringRedisTemplate.opsForHash().keys(redisKey).stream().map(Object::toString).collect(Collectors.toSet());
        if (ObjectUtils.isEmpty(members)) {
            members = Collections.emptySet();
        }
        return members;
    }

    /**
     * 获取设备活跃信息
     * @param deviceId
     * @return
     */
    public String getDeviceActiveInfo(String deviceId) {
        return stringRedisTemplate.opsForValue().get(String.format(MqttConstants.IOT_DEV_ACTIVE, deviceId));
    }
}
