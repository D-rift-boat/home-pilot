package com.dboat.iot.mqtt;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.common.constants.MqttConstants;
import com.dboat.iot.dto.mqtt.*;
import com.dboat.iot.entity.SensorData;
import com.dboat.iot.enums.SensorStatusEnum;
import com.dboat.iot.service.DeviceLogService;
import com.dboat.iot.service.DeviceService;
import com.dboat.iot.service.SensorDataService;
import com.dboat.iot.utils.DeviceStateStore;
import com.dboat.iot.utils.JsonUtils;
import com.dboat.iot.ws.DeviceWebSocketHandler;
import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT 消息处理器
 * <p>
 * 核心职责：
 * <ul>
 *   <li>接收并路由所有入站 MQTT 消息（传感器数据上报 + EMQX 设备断连事件）</li>
 *   <li>解析 ESP32-S3 设备上报的 JSON 传感器数据</li>
 *   <li>执行流式告警计算（In-Flight Alerting），在数据入库前完成异常检测</li>
 *   <li>刷新 Redis 设备实时状态</li>
 *   <li>异步写入 InfluxDB 时序数据库</li>
 *   <li>处理设备离线事件，生成离线告警上下文</li>
 *   <li>向设备端下发指令（通过 iot/cmd/{deviceId} 主题，标准 DOWN_CMD 格式）</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Component
public class MqttMessageHandler {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    /**
     * 设备数据上报主题前缀（完整主题: iot/sensor/upload/{device_id}）
     */
    private static final String TOPIC_SENSOR_UPLOAD_PREFIX = "iot/sensor/upload/";

    /**
     * EMQX 设备断连事件主题（通配符匹配所有 Broker 节点的所有客户端断连）
     */
    private static final String TOPIC_CLIENT_DISCONNECTED = "$SYS/brokers/+/clients/+/disconnected";

    /**
     * EMQX 设备状态事件主题（通配符匹配所有 Broker 节点的所有客户端断连）
     */
    private static final String TOPIC_CLIENT_STAUTS_PREFIX = "iot/device/status/";

    /**
     * 设备指令下发主题前缀（完整主题: iot/cmd/{deviceId}）
     */
    private static final String TOPIC_COMMAND_PREFIX = "iot/cmd/";

    /** MQTT 客户端管理器，用于发布指令消息 */
    private final MqttClientManager mqttClientManager;
    /** 设备资产服务，用于自动注册设备 */
    private final DeviceService deviceService;
    /** 传感器数据服务，用于异步写入 InfluxDB */
    private final SensorDataService sensorDataService;
    /** 设备日志服务，用于记录设备上下线、异常等事件 */
    private final DeviceLogService deviceLogService;
    /** Redis 设备状态存储工具，用于刷新设备实时状态 */
    private final DeviceStateStore deviceStateStore;
    /** WebSocket 处理器，用于向前端广播实时数据 */
    private final DeviceWebSocketHandler webSocketHandler;

    /**
     * 构造方法注入依赖（使用 @Lazy 解决与 MqttClientManager 的循环依赖）
     *
     * @param mqttClientManager    MQTT 客户端管理器（延迟注入）
     * @param deviceService        设备资产服务
     * @param sensorDataService    传感器数据服务
     * @param deviceLogService     设备日志服务
     * @param deviceStateStore     Redis 状态存储工具
     * @param webSocketHandler     WebSocket 处理器（实时数据广播）
     */
    public MqttMessageHandler(@Lazy MqttClientManager mqttClientManager,
                               DeviceService deviceService,
                               SensorDataService sensorDataService,
                               DeviceLogService deviceLogService,
                               DeviceStateStore deviceStateStore,
                               DeviceWebSocketHandler webSocketHandler) {
        this.mqttClientManager = mqttClientManager;
        this.deviceService = deviceService;
        this.sensorDataService = sensorDataService;
        this.deviceLogService = deviceLogService;
        this.deviceStateStore = deviceStateStore;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * MQTT 消息入口路由
     * <p>
     * 根据主题前缀将消息分发到对应的处理方法：
     * <ul>
     *   <li>iot/sensor/upload/* → 传感器数据处理</li>
     *   <li>$SYS/brokers/clients/disconnected → 设备断连处理</li>
     * </ul>
     * </p>
     *
     * @param topic   MQTT 主题
     * @param message MQTT 消息体
     */
    public void handleMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("Received MQTT message on topic [{}]: {}", topic, payload);

        //解析上报数据 （按统一格式解析）
        MqttUpDataMessage upDataMessage = JSONObject.parseObject(payload, MqttUpDataMessage.class);
        MqttMessageHeader header = upDataMessage.getHeader();

        try {
            if (topic.startsWith(TOPIC_SENSOR_UPLOAD_PREFIX)) {
                handleSensorUpload(topic, upDataMessage);
            } else if (topic.startsWith(TOPIC_CLIENT_STAUTS_PREFIX)) {
                if (header.getMsgType().equals(MqttConstants.ONLINE)){
                    log.info("device online: {}", topic);
                } else if (header.getMsgType().equals(MqttConstants.OFFLINE)) {
                    handleDeviceDisconnected(topic, upDataMessage);
                    log.info("device offline: {}", topic);
                }
            } else {
                log.warn("Unhandled MQTT topic: {}", topic);
            }
        } catch (Exception e) {
            log.error("Failed to handle MQTT message on topic [{}]: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * 处理传感器数据上报（UP_DATA 标准格式）
     * <p>
     * 完整处理流程：
     * <ol>
     *   <li>解析 header + payload 结构</li>
     *   <li>自动注册设备（首次上报）</li>
     *   <li>时序入库 InfluxDB</li>
     *   <li>刷新 Redis 设备快照（{userId}:{deviceId}:latest，TTL 24h）+ 维护在线数</li>
     *   <li>组装 WebSocket 推送报文，广播给所有前端会话</li>
     * </ol>
     * </p>
     */
    private void handleSensorUpload(String topic, MqttUpDataMessage message) {
        MqttMessageHeader header = message.getHeader();
        MqttMessagePayload payload = message.getPayload();
        if (ObjectUtils.anyNull(header, payload)) {
            log.error("Invalid UP_DATA payload: {}", payload);
            return;
        }

        // 校验消息类型
        if (!"UP_DATA".equals(header.getMsgType())) {
            log.warn("Unexpected msgType in sensor upload: {}", header.getMsgType());
            return;
        }

        String deviceId = header.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            log.error("Missing deviceId in UP_DATA header: {}", payload);
            return;
        }

        log.info("UP_DATA received from device [{}], traceId={}", deviceId, header.getTraceId());

        // 自动注册设备（首次上报时自动创建）
        deviceService.autoRegister(deviceId);

        // 解析传感器状态（从 payload.sensorStatus 中获取各传感器独立状态）
        Integer deviceStatus = payload.getDeviceStatus() != null ? payload.getDeviceStatus() : SensorStatusEnum.NORMAL.getCode();
        Integer aht20Status = 1;
        Integer bmp280Status = 1;
        if (payload.getSensorStatus() != null) {
            aht20Status = payload.getSensorStatus().getAht20() != null ? payload.getSensorStatus().getAht20() : 1;
            bmp280Status = payload.getSensorStatus().getBmp280() != null ? payload  .getSensorStatus().getBmp280() : 1;
        }

        // ========== 异步写入 InfluxDB ==========
        SensorData sensorData = buildSensorData(deviceId, payload, deviceStatus, aht20Status, bmp280Status);
        sensorDataService.saveSensorData(sensorData);

        // ========== 刷新 Redis 设备快照 + 维护在线数 ==========
        // 构建完整设备数据 JSON（存入 {userId}:{deviceId}:latest，TTL 24h）
        Map deviceDataMap = buildDeviceLatestMap(deviceId, deviceStatus, aht20Status, bmp280Status, payload, header.getTimestamp());
        boolean isNewDevice = deviceStateStore.updateDeviceLatestData(deviceId, deviceDataMap);
        if (isNewDevice) {
            log.info("Device first report / re-online, online count incremented: {}", deviceId);
        }

        // ========== WebSocket 实时推送 ==========
        try {
            String wsMessage = buildWebSocketPushMessage(message);
            webSocketHandler.broadcastToAll(wsMessage);
        } catch (Exception e) {
            log.warn("Failed to broadcast WS message for device [{}]: {}", deviceId, e.getMessage());
        }

        log.info("Processed UP_DATA from device: {}", deviceId);
    }

    /**
     * 内存告警计算：检查传感器状态是否异常
     */
    private void checkAndTriggerSensorAlarm(String deviceId, int sensorStatus, int aht20Status,
                                             int bmp280Status, String rawPayload) {
        boolean hasAlarm = false;
        StringBuilder alarmDesc = new StringBuilder();

        if (SensorStatusEnum.isAbnormal(sensorStatus)) {
            hasAlarm = true;
            alarmDesc.append(String.format("sensor_status=%d(%s) ",
                    sensorStatus, SensorStatusEnum.fromCode(sensorStatus).getNameCn()));
        }
        if (SensorStatusEnum.isAbnormal(aht20Status)) {
            hasAlarm = true;
            alarmDesc.append(String.format("aht20_status=%d(%s) ",
                    aht20Status, SensorStatusEnum.fromCode(aht20Status).getNameCn()));
        }
        if (SensorStatusEnum.isAbnormal(bmp280Status)) {
            hasAlarm = true;
            alarmDesc.append(String.format("bmp280_status=%d(%s) ",
                    bmp280Status, SensorStatusEnum.fromCode(bmp280Status).getNameCn()));
        }

        if (hasAlarm) {
            JSONObject detail = new JSONObject();
            detail.put("sensor_status", sensorStatus);
            detail.put("aht20_status", aht20Status);
            detail.put("bmp280_status", bmp280Status);
            detail.put("description", alarmDesc.toString().trim());
            detail.put("raw_payload", rawPayload);

            deviceLogService.logDeviceAbnormal(deviceId, sensorStatus, alarmDesc.toString().trim(), detail.toJSONString());
            log.warn("Sensor fault log recorded for device [{}]: {}", deviceId, alarmDesc);
        }
    }

    /**
     * 处理 EMQX 设备断连事件
     * 主题格式: $SYS/brokers/{node}/clients/{client_id}/disconnected
     * <p>
     * 设备离线时：删除 Redis 最新数据 Key + DECR 在线数 + 记录离线日志
     * </p>
     */
    private void handleDeviceDisconnected(String topic, MqttUpDataMessage message) {
        // 从主题中提取 clientId（即 device_id）
        if (ObjectUtils.isEmpty(message.getHeader())) {
            log.warn("Invalid disconnected topic format: {}", topic);
            return;
        }
        String deviceId = message.getHeader().getDeviceId();
        log.warn("Device disconnected event received for device: {}", deviceId);

        // 1. 删除 Redis 最新数据 Key + DECR 在线数
        deviceStateStore.userDeviceOffline(deviceId);

        // 2. 从 InfluxDB 查询离线前最后一条传感器数据作为离线上下文
        String offlineContext = buildOfflineContext(deviceId);

        // 3. 记录设备离线日志
        deviceLogService.logDeviceOffline(deviceId, offlineContext);

        // ========== WebSocket 实时推送（设备离线通知） ==========
        try {
            String wsMessage = buildDeviceOfflineMessage(deviceId);
            webSocketHandler.broadcastToAll(wsMessage);
        } catch (Exception e) {
            log.warn("Failed to broadcast WS offline message for device [{}]: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 构建离线日志上下文信息
     * <p>
     * 从 InfluxDB 查询设备离线前最后一条传感器数据，
     * 作为离线日志的附加上下文（最后活跃时间、传感器状态、温湿度等）。
     * </p>
     *
     * @param deviceId 设备ID
     * @return JSON 格式的离线上下文，查询失败时返回 null
     */
    private String buildOfflineContext(String deviceId) {
        try {
            SensorData lastData = sensorDataService.getLatestSensorDataRaw(deviceId);
            if (lastData != null) {
                JSONObject context = new JSONObject();
                context.put("last_seen_time", lastData.getReportTime() != null
                        ? lastData.getReportTime().toString() : "unknown");
                context.put("sensor_status", lastData.getSensorStatus());
                context.put("aht20_status", lastData.getAht20Status());
                context.put("bmp280_status", lastData.getBmp280Status());
                context.put("temperature_aht", lastData.getTemperatureAht());
                context.put("humidity", lastData.getHumidity());
                return context.toJSONString();
            }
        } catch (Exception e) {
            log.warn("Failed to query last sensor data for device [{}]: {}", deviceId, e.getMessage());
        }
        return null;
    }

    /**
     * 根据 UP_DATA payload 构建 SensorData 实体对象
     *
     * @param deviceId      设备ID（从 header 中提取）
     * @param payload       UP_DATA 消息体
     * @param sensorStatus  传感器整体状态码
     * @param aht20Status   AHT20 温湿度传感器状态码
     * @param bmp280Status  BMP280 气压传感器状态码
     * @return 填充完毕的 SensorData 实体
     */
    private SensorData buildSensorData(String deviceId, MqttMessagePayload payload,
                                        int sensorStatus, int aht20Status, int bmp280Status) {
        SensorData sensorData = new SensorData();
        sensorData.setDeviceId(deviceId);

        // 从 envData 中提取环境监测数据（驼峰字段，零映射成本）
        MqttUpEnvData env = payload.getEnvData();
        if (env != null) {
            sensorData.setTemperatureAht(env.getTempAht());
            sensorData.setTemperatureBmp(env.getTempBmp());
            sensorData.setHumidity(env.getHumidity());
            sensorData.setPressureHpa(env.getPressureHpa());
            sensorData.setAltitudeM(env.getAltitude());
        }

        sensorData.setSensorStatus(sensorStatus);
        sensorData.setAht20Status(aht20Status);
        sensorData.setBmp280Status(bmp280Status);
        sensorData.setReportTime(Instant.now());
        return sensorData;
    }

    /**
     * 构建设备最新数据 JSON（存入 Redis {userId}:{deviceId}:latest）
     * <p>
     * 格式：
     * <pre>
     * {
     *   "deviceId": "esp32-S3-001",
     *   "deviceStatus": 1,
     *   "tempAht": 27.11, "tempBmp": 28.25,
     *   "humidity": 62.10, "pressureHpa": 990.20, "altitude": -27.34,
     *   "aht20Status": 1, "bmp280Status": 1,
     *   "timestamp": 1755623345216
     * }
     * </pre>
     * </p>
     */
    private HashMap<String, String> buildDeviceLatestMap(String deviceId, Integer deviceStatus,
                                                         Integer aht20Status, Integer bmp280Status,
                                         MqttMessagePayload body, Long deviceTimestamp) {
        HashMap<String, String> map = new HashMap<>();
        map.put("deviceId", deviceId);
        map.put("deviceStatus", deviceStatus.toString());
        map.put("aht20Status", aht20Status.toString());
        map.put("bmp280Status", bmp280Status.toString());
        map.put("timestamp", Long.valueOf(deviceTimestamp != null ? deviceTimestamp : System.currentTimeMillis()).toString());

        // 填充 envData 字段
        MqttUpEnvData env = body.getEnvData(); // Changed from body.getEnvData() to payload.getEnvData()
        if (env != null) {
            map.put("tempAht", formatBigDecimal(env.getTempAht()));
            map.put("tempBmp", formatBigDecimal(env.getTempBmp()));
            map.put("humidity", formatBigDecimal(env.getHumidity()));
            map.put("pressureHpa", formatBigDecimal(env.getPressureHpa()));
            map.put("altitude", formatBigDecimal(env.getAltitude()))        ;
        }
        return map;
    }

    /**
     * 格式化BigDecimal字段
     */
    private String formatBigDecimal(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 构建 WebSocket 实时推送报文（REAL_TIME_DATA 格式）
     * <p>
     * 入参为完整的 MqttUpDataMessage，内部提取 header/payload 中的所需字段。
     * 格式：
     * <pre>
     * {
     *   "type": "REAL_TIME_DATA",
     *   "data": { "tempAht": ..., "humidity": ..., "pressureHpa": ..., "altitude": ..., "onlineCount": N },
     *   "device": { "deviceId": ..., "deviceStatus": ..., "aht20Status": ..., "bmp280Status": ..., "tempBmp": ... },
     *   "timestamp": ...
     * }
     * </pre>
     * </p>
     *
     * @param upDataMessage MQTT 上行消息（header + payload）
     * @return JSON 格式的 WebSocket 推送报文
     */
    private String buildWebSocketPushMessage(MqttUpDataMessage upDataMessage) {
        MqttMessageHeader header = upDataMessage.getHeader();
        MqttMessagePayload payload = upDataMessage.getPayload();
        String deviceId = header.getDeviceId();
        Long deviceTimestamp = header.getTimestamp();

        // 从 payload 中提取传感器状态（带默认值）
        int deviceStatus = payload.getDeviceStatus() != null ? payload.getDeviceStatus() : 1;
        int aht20Status = SensorStatusEnum.NORMAL.getCode();
        int bmp280Status = SensorStatusEnum.NORMAL.getCode();
        if (payload.getSensorStatus() != null) {
            aht20Status = payload.getSensorStatus().getAht20() != null ? payload.getSensorStatus().getAht20() : SensorStatusEnum.NORMAL.getCode();
            bmp280Status = payload.getSensorStatus().getBmp280() != null ? payload.getSensorStatus().getBmp280() : SensorStatusEnum.NORMAL.getCode();
        }

        JSONObject message = new JSONObject();
        message.put("type", "REAL_TIME_DATA");
        message.put("timestamp", deviceTimestamp != null ? deviceTimestamp : System.currentTimeMillis());

        // data 部分：跨页面共享的实时汇总数据
        JSONObject data = new JSONObject();
        //long onlineCount = deviceStateStore.getUserDeviceOnlineCount();
        MqttUpEnvData env = payload.getEnvData();
        if (env != null) {
            data.put("tempAht", env.getTempAht());
            data.put("humidity", env.getHumidity());
            data.put("pressureHpa", env.getPressureHpa());
            data.put("altitude", env.getAltitude());
        }
        //data.put("onlineCount", onlineCount);
        message.put("data", data);

        // device 部分：设备级详细信息
        JSONObject device = new JSONObject();
        device.put("deviceId", deviceId);
        device.put("deviceStatus", deviceStatus);
        device.put("aht20Status", aht20Status);
        device.put("bmp280Status", bmp280Status);
        if (env != null) {
            device.put("tempBmp", env.getTempBmp());
        }
        message.put("device", device);

        return message.toJSONString();
    }

    /**
     * 构建设备离线 WebSocket 推送报文（DEVICE_OFFLINE 格式）
     * <p>
     * 设备断连时推送给前端，通知前端更新设备离线状态。
     * 格式：
     * <pre>
     * {
     *   "type": "DEVICE_OFFLINE",
     *   "deviceId": "esp32-S3-001",
     *   "timestamp": ...
     * }
     * </pre>
     * </p>
     *
     * @param deviceId 离线设备ID
     * @return JSON 格式的离线通知报文
     */
    private String buildDeviceOfflineMessage(String deviceId) {
        JSONObject message = new JSONObject();
        message.put("type", "DEVICE_OFFLINE");
        message.put("deviceId", deviceId);
        message.put("timestamp", System.currentTimeMillis());
        return message.toJSONString();
    }

    /**
     * 向指定设备发布指令消息（DOWN_CMD 标准格式）
     * <p>
     * 将指令封装为标准 header + payload JSON 格式：
     * <ul>
     *   <li>header: msgType=DOWN_CMD, requestId(UUID), deviceId, timestamp, timeout</li>
     *   <li>payload: cmdCode, params</li>
     * </ul>
     * 通过 iot/cmd/{deviceId} 主题发送到设备端。
     * </p>
     *
     * @param deviceId 目标设备ID
     * @param cmdCode  指令编码（如 device_restart、sensor_calibrate、light_switch）
     * @param params   指令参数（可为 null）
     * @param timeout  指令超时时间（毫秒）
     * @return requestId 指令唯一ID，用于异步应答匹配
     */
    public String publishCommand(String deviceId, String cmdCode, Map<String, Object> params, long timeout) {
        String requestId = UUID.randomUUID().toString();

        // 构建标准 DOWN_CMD 消息
        MqttDownCmdMessage cmdMsg = new MqttDownCmdMessage();

        // 构建 header
        MqttDownCmdMessage.Header header = new MqttDownCmdMessage.Header();
        header.setMsgType("DOWN_CMD");
        header.setRequestId(requestId);
        header.setDeviceId(deviceId);
        header.setTimestamp(System.currentTimeMillis());
        header.setTimeout(timeout);
        cmdMsg.setHeader(header);

        // 构建 payload
        MqttDownCmdMessage.Payload body = new MqttDownCmdMessage.Payload();
        body.setCmdCode(cmdCode);
        body.setParams(params != null ? params : new HashMap<>());
        cmdMsg.setPayload(body);

        // 序列化并发布
        String topic = TOPIC_COMMAND_PREFIX + deviceId;
        String jsonPayload = JsonUtils.toJSONString(cmdMsg);
        mqttClientManager.publish(topic, jsonPayload, 1);
        log.info("Published DOWN_CMD to device [{}], cmdCode={}, requestId={}", deviceId, cmdCode, requestId);

        return requestId;
    }
}
