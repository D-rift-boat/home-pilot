package com.dboat.iot.utils;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.dto.ws.IotDevLineDTO;
import com.dboat.iot.dto.ws.WsUploadDataDTO;
import com.dboat.iot.enums.WsTypeEnum;
import com.dboat.iot.ws.LocalWsSessionManager;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RMapCache;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.dboat.iot.service.UserDeviceRelService;
import org.springframework.context.annotation.Lazy;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dboat.iot.common.constants.MqttConstants.IOT_DEVICE_ONLINE_PREFIX;
import static com.dboat.iot.common.constants.WebSocketConstants.WS_ROUTER_PREFIX;

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

    /** Redisson Lua 脚本执行时使用的 String Codec 全限定类名 */
    private static final String STRING_CODEC_NAME = "org.redisson.client.codec.StringCodec";

    /**
     * 默认用户ID（前端写死 admin）
     */
    private static final String DEFAULT_USER_ID = "admin";

    /**
     * 设备最新数据 Key 前缀
     */
    private static final String LATEST_PREFIX = "ws:iot_device:latest:";

    /**
     * 用户在线iot设备列表 前缀
     */
    private static final String ONLINE_IOT_DEVICE_LIST_PREFIX = "ws:iot_device:online_list:";

    /**
     * 用户在线设备总数 Key
     */
    private static final String USER_ONLINE_COUNT_PREFIX = "ws:stat:online_user_device_count:";

    /**
     * 在线iot设备总数 Key
     */
    private static final String IOT_ONLINE_COUNT_PREFIX = "ws:stat:online_iot_device_count:";

    /**
     * 设备最新数据 TTL：24小时（长时间无上报自动过期，判定设备离线）
     */
    private static final long LATEST_DATA_TTL_HOURS = 24;

    /**
     * 本地会话管理器，用于向前端广播实时数据
     */
    @Resource
    private LocalWsSessionManager localWsSessionManager;


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
     * Lua 脚本：仅当值为正数时递减
     */
    private static final String DECR_IF_POSITIVE_LUA =
            "local val = tonumber(redis.call('get', KEYS[1])) " +
                    "if val and val > 0 then " +
                    "    return redis.call('decr', KEYS[1]) " +
                    "end " +
                    "return 0";

    /**
     * Lua 脚本：iot设备上线
     */
    private static final String LUA_IOT_ONLINE = """
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[1], 86400)
        return redis.call('HLEN', KEYS[1])
        """;

    /**
     * Lua 脚本：原子化 Hash 批量写入 + 设置整体过期时间
     * <p>
     * ARGV 约定：ARGV[1] = TTL秒数，ARGV[2..N] = field-value 对
     * 分离 TTL 参数，避免 unpack 展开时混入 HSET 导致参数个数为奇数。
     * </p>
     */
    private static final String HASH_PUT_ALL_WITH_EXPIRE_LUA = """
            redis.call('HSET', KEYS[1], unpack(ARGV, 2, #ARGV))
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            return 1
            """;

    /**
     * Lua 脚本：仅当值为正数时递减
     */
    private static final String LUA_IOT_OFFLINE = """
            redis.call('HDEL', KEYS[1], ARGV[1])
            return redis.call('HLEN', KEYS[1])
            """;

    /**
     * Lua 脚本：仅当值为正数时递减
     */
    private final DefaultRedisScript<Long> decrScript =
            new DefaultRedisScript<>(DECR_IF_POSITIVE_LUA, Long.class);



    // ==================== Key 构建 ====================

    /**
     * 构建设备最新数据 Key（使用默认 ws:iotDevice:latest:iot_deviceId
     */
    private String buildLatestKey(String deviceId) {
        return LATEST_PREFIX + deviceId;
    }

    /**
     * 构建用户在线设备总数 Key：ws:stat:online_user_device_count:{userId}
     */
    private String buildUserOnlineCountKey(String userId) {
        return USER_ONLINE_COUNT_PREFIX + userId;
    }

    /**
     * 构建用户在线iot设备总数 Key：ws:stat:online_iot_device_count::{userId}
     */
    private String buildIotOnlineCountKey(String userId) {
        return IOT_ONLINE_COUNT_PREFIX + userId;
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
        stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(deviceDataMap), LATEST_DATA_TTL_HOURS, TimeUnit.HOURS);

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

            String iotOnlineKey = IOT_DEVICE_ONLINE_PREFIX + userId;
            // 原子化：批量写入 + 设置整key过期
            atomicHashPutAll(iotOnlineKey, iotOnlineMap, LATEST_DATA_TTL_HOURS, TimeUnit.HOURS);
        }


        log.debug("Refreshed device latest data: {}", deviceId);
        return true;


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
                HASH_PUT_ALL_WITH_EXPIRE_LUA,
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
        if(ObjectUtils.isEmpty(subscribedDeviceIds)){
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
                .humidity(getStr(map, "humidity"))
                .pressureHpa(getStr(map, "pressureHpa"))
                .altitude(getStr(map, "altitude"))
                .iotDeviceOnlineCount(getStr(map, "iotDeviceOnlineCount"))
                .userDeviceOnlineCount(getStr(map, "userDeviceOnlineCount"))
                .build();

        WsUploadDataDTO.DeviceDTO deviceDTO = WsUploadDataDTO.DeviceDTO.builder()
                .deviceId(getStr(map, "deviceId"))
                .deviceStatus(getStr(map, "deviceStatus"))
                .aht20Status(getStr(map, "aht20Status"))
                .bmp280Status(getStr(map, "bmp280Status"))
                .tempBmp(getStr(map, "tempBmp"))
                .build();

        // type、timestamp 程序内部生成，不从redis读取
        WsUploadDataDTO wsUploadDataDTO = WsUploadDataDTO.of(dataDTO, deviceDTO);
        wsUploadDataDTO.setTimestamp(getStr(map, "timestamp"));
        return wsUploadDataDTO;

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
     * 设备离线：删除最新数据 Key + DECR 在线数
     * 在线设备数量 -1
     *
     * @param userId 用户ID
     * @return true=成功删除（设备确实离线），false=Key 已不存在
     */
    public boolean userDeviceOffline(String userId) {
        decrementOnlineUserDevCount(userId);
        log.info("Device offline, DECR online count: {}", userId);
        return true;
    }

    /**
     * 设备离线：删除最新数据 Key + DECR 在线数
     * 在线设备数量 -1
     *
     * @return true=成功删除（设备确实离线），false=Key 已不存在
     */
    public boolean iotDeviceOffline(String iotDeviceId) {
        // 获取所有订阅该设备的用户列表
        Set<String> userIdSet = userDeviceRelService.getSubscriberUserIds(iotDeviceId);
        // 更新所属用户的在线iot设备列表 删除该设备
        for (String userId : userIdSet) {
            Long userIotDevOnlineCount = offlineUserIotDev(userId, iotDeviceId);
            WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
            wsUploadDataDTO.setType(WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode());
            WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
            WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
            deviceDTO.setDeviceId("web-001");
            dataDTO.setIotDeviceOnlineCount(String.valueOf(userIotDevOnlineCount));
            wsUploadDataDTO.setData((dataDTO));
            wsUploadDataDTO.setDevice(deviceDTO);
            localWsSessionManager.broadcastToAll(JSONObject.toJSONString(wsUploadDataDTO));
            log.info("Device offline", userId);
        }

        return true;
    }

    /**
     * iot设备上线：
     * 新增/刷新 用户在线iot设备列表 并ws通知订阅设备的相关用户
     *
     * @return 在线设备数量
     */
    public boolean iotDeviceOnline(String iotDeviceId) {
        Set<String> userIdSet = userDeviceRelService.getSubscriberUserIds(iotDeviceId);
        log.info("Device subscribe user list：{}", userIdSet);

        // 更新所属用户的在线iot设备列表 该设备加入在线列表
        for (String userId : userIdSet) {
            Long userIotDevOnlineCount = onlineUserIotDev(userId, iotDeviceId);
            WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
            wsUploadDataDTO.setType(WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode());
            WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
            WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
            deviceDTO.setDeviceId("web-001");
            dataDTO.setIotDeviceOnlineCount(String.valueOf(userIotDevOnlineCount));
            wsUploadDataDTO.setData((dataDTO));
            wsUploadDataDTO.setDevice(deviceDTO);
            localWsSessionManager.broadcastToAll(JSONObject.toJSONString(wsUploadDataDTO));
            log.info("Device online iotDeviceId：{}", iotDeviceId);
        }

        return true;
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
     * 在线设备数 +1（原子操作）
     */
    public void incrementOnlineIotCount(String userId) {
        stringRedisTemplate.opsForValue().increment(buildIotOnlineCountKey(userId));
    }

    /**
     * 用户在线设备数 +1（原子操作）
     */
    public Long incrementOnlineUserDevCount(String userId) {
        Long count = stringRedisTemplate.opsForValue().increment(buildUserOnlineCountKey(userId));
        return count == null ? 1 : count;
    }

    /**
     * 删除用户在线设备列表中的指定设备
     */
    public Long offlineUserIotDev(String userId,String iotDeviceId) {
        String redisKey = IOT_DEVICE_ONLINE_PREFIX + userId;
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
    public Long onlineUserIotDev(String userId,String iotDeviceId) {
        String redisKey = IOT_DEVICE_ONLINE_PREFIX + userId;
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
     * 在线设备数 -1（原子操作，保证不低于 0）
     */
    public Long decrementOnlineUserDevCount(String userId) {
        String countKey = buildUserOnlineCountKey(userId);
        Long count = stringRedisTemplate.execute(decrScript, Collections.singletonList(countKey));
        Long result = count == null ? 0 : count;

        // 递减后如果为0，可以触发推送"设备全部离线"事件
        if (result == 0) {
            log.info("All devices offline for user: {}", userId);
            // pushAllOfflineEvent(userId);
        }

        return result;
    }

    /**
     * 获取用户在线iot设备总数
     *
     * @return 在线设备数，Key 不存在时返回 0
     */
    public Long getUserIotDeviceOnlineCount(String userId) {
        String redisKey = IOT_DEVICE_ONLINE_PREFIX + userId;

        Long countStr = stringRedisTemplate.opsForHash().size(redisKey);
        if (ObjectUtils.isEmpty(countStr)) {
            countStr = 0L;
        }
        return countStr;
    }

    /**
     * 获取用户在线设备总数
     *
     * @return 在线设备数，Key 不存在时返回 0
     */
    public Long getUserDeviceOnlineCount(String userId) {
        String redisKey = WS_ROUTER_PREFIX + userId;

        Long countStr = stringRedisTemplate.opsForHash().size(redisKey);
        if (ObjectUtils.isEmpty(countStr)) {
            countStr = 0L;
        }
        return countStr;
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
        String redisKey = IOT_DEVICE_ONLINE_PREFIX + userId;

        Set<String> members = stringRedisTemplate.opsForHash().keys(redisKey).stream().map(Object::toString).collect(Collectors.toSet());
        if (ObjectUtils.isEmpty(members)) {
            members = Collections.emptySet();
        }
        return members;
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
    //private String extractDeviceIdFromKey(String key) {
    //    String prefix = DEFAULT_USER_ID + ":";
    //    if (!key.startsWith(prefix) || !key.endsWith(LATEST_SUFFIX)) {
    //        return null;
    //    }
    //    return key.substring(prefix.length(), key.length() - LATEST_SUFFIX.length());
    //}
}
