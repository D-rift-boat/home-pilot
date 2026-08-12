package com.dboat.iot.mqtt;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.dto.mqtt.MqttDownCmdMessage;
import com.dboat.iot.dto.mqtt.MqttUpDataMessage;
import com.dboat.iot.entity.SensorData;
import com.dboat.iot.enums.SensorStatusEnum;
import com.dboat.iot.service.DeviceLogService;
import com.dboat.iot.service.DeviceService;
import com.dboat.iot.service.SensorDataService;
import com.dboat.iot.utils.DeviceStateStore;
import com.dboat.iot.utils.JsonUtils;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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

    /**
     * 构造方法注入依赖（使用 @Lazy 解决与 MqttClientManager 的循环依赖）
     *
     * @param mqttClientManager    MQTT 客户端管理器（延迟注入）
     * @param deviceService        设备资产服务
     * @param sensorDataService    传感器数据服务
     * @param deviceLogService     设备日志服务
     * @param deviceStateStore     Redis 状态存储工具
     */
    public MqttMessageHandler(@Lazy MqttClientManager mqttClientManager,
                               DeviceService deviceService,
                               SensorDataService sensorDataService,
                               DeviceLogService deviceLogService,
                               DeviceStateStore deviceStateStore) {
        this.mqttClientManager = mqttClientManager;
        this.deviceService = deviceService;
        this.sensorDataService = sensorDataService;
        this.deviceLogService = deviceLogService;
        this.deviceStateStore = deviceStateStore;
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
        String payload = new String(message.getPayload());
        log.info("Received MQTT message on topic [{}]: {}", topic, payload);

        try {
            if (topic.startsWith(TOPIC_SENSOR_UPLOAD_PREFIX)) {
                handleSensorUpload(topic, payload);
            } else if (topic.matches("\\$SYS/brokers/[^/]+/clients/[^/]+/disconnected")) {
                handleDeviceDisconnected(topic, payload);
            } else if (topic.startsWith(TOPIC_CLIENT_STAUTS_PREFIX)) {
                log.info("device online: {}", topic);
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
     * 解析 header + payload 结构：
     * header 中提取 deviceId、traceId、timestamp 等元数据；
     * payload 中提取 deviceStatus、sensorStatus、envData 等业务数据。
     * </p>
     * 流程: 解析JSON → 内存告警计算 → 刷新Redis → 异步写入InfluxDB
     */
    private void handleSensorUpload(String topic, String payload) {
        // 使用 Fastjson2 解析为 UP_DATA DTO
        MqttUpDataMessage msg = JsonUtils.parseObject(payload, MqttUpDataMessage.class);
        if (msg == null || msg.getHeader() == null || msg.getPayload() == null) {
            log.error("Invalid UP_DATA payload: {}", payload);
            return;
        }

        MqttUpDataMessage.Header header = msg.getHeader();
        MqttUpDataMessage.Payload body = msg.getPayload();

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
        int sensorStatus = body.getDeviceStatus() != null ? body.getDeviceStatus() : 1;
        int aht20Status = 1;
        int bmp280Status = 1;
        if (body.getSensorStatus() != null) {
            aht20Status = body.getSensorStatus().getAht20() != null ? body.getSensorStatus().getAht20() : 1;
            bmp280Status = body.getSensorStatus().getBmp280() != null ? body.getSensorStatus().getBmp280() : 1;
        }

        // ========== 流式告警计算（In-Flight Alerting，数据入库前执行） ==========
        //checkAndTriggerSensorAlarm(deviceId, sensorStatus, aht20Status, bmp280Status, payload);

        // ========== 刷新 Redis 设备实时状态 ==========
        deviceStateStore.refreshDeviceState(deviceId, sensorStatus, aht20Status, bmp280Status);

        // ========== 异步写入 InfluxDB ==========
        SensorData sensorData = buildSensorData(deviceId, body, sensorStatus, aht20Status, bmp280Status);
        sensorDataService.saveSensorData(sensorData);
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
     */
    private void handleDeviceDisconnected(String topic, String payload) {
        // 从主题中提取 clientId（即 device_id）
        String[] parts = topic.split("/");
        if (parts.length < 5) {
            log.warn("Invalid disconnected topic format: {}", topic);
            return;
        }
        String deviceId = parts[4]; // $SYS/brokers/{node}/clients/{device_id}/disconnected
        log.warn("Device disconnected event received for device: {}", deviceId);

        // 1. 更新 Redis 在线状态为 OFFLINE
        deviceStateStore.setDeviceOffline(deviceId);

        // 2. 从 InfluxDB 查询离线前最后一条传感器数据作为离线上下文
        String offlineContext = buildOfflineContext(deviceId);

        // 3. 记录设备离线日志
        deviceLogService.logDeviceOffline(deviceId, offlineContext);
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
    private SensorData buildSensorData(String deviceId, MqttUpDataMessage.Payload payload,
                                        int sensorStatus, int aht20Status, int bmp280Status) {
        SensorData sensorData = new SensorData();
        sensorData.setDeviceId(deviceId);

        // 从 envData 中提取环境监测数据（驼峰字段，零映射成本）
        MqttUpDataMessage.EnvData env = payload.getEnvData();
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
