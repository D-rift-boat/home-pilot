package com.dboat.iot.mqtt;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dboat.iot.common.constants.DeviceLogEnum;
import com.dboat.iot.common.constants.MqttConstants;
import com.dboat.iot.dto.mqtt.*;
import com.dboat.iot.dto.ws.WsUploadDataDTO;
import com.dboat.iot.entity.*;
import com.dboat.iot.enums.WsTypeEnum;
import com.dboat.iot.enums.SensorStatusEnum;
import com.dboat.iot.service.*;
import com.dboat.iot.service.ws.WsDistributedPushService;
import com.dboat.iot.service.ws.DeviceRedisService;
import com.dboat.iot.utils.DateTimeUtils;
import com.dboat.iot.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MQTT 消息处理器
 * <p>
 * 核心职责：
 * <ul>
 *   <li>接收并路由所有入站 MQTT 消息（传感器数据上报 + EMQX 设备断连事件）</li>
 *   <li>解析 ESP32-S3 设备上报的 JSON 传感器数据</li>
 *   <li>执行流式告警计算（In-Flight Alerting），在数据入库前完成异常检测</li>
 *   <li>刷新 Redis 设备实时状态</li>
 *   <li>直连订阅模式下异步写入 InfluxDB 时序数据库（关闭时由 EMQX 规则引擎转发 Kafka，消费端写入）</li>
 *   <li>处理设备离线事件，生成离线告警上下文</li>
 *   <li>向设备端下发指令（通过 iot/cmd/{deviceId} 主题，标准 DOWN_CMD 格式）</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

	/**
	 * 日志记录器
	 */
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

	/**
	 * MQTT 客户端管理器，用于发布指令消息
	 */
	//private final MqttClientManager mqttClientManager;
	/**
	 * 设备资产服务，用于自动注册设备
	 */
	private final DeviceService deviceService;
	/**
	 * 传感器数据服务，用于写入/查询 InfluxDB 数据
	 */
	private final TelemetryDataService telemetryDataService;
	/**
	 * 设备日志服务，用于记录设备上下线、异常等事件
	 */
	private final DeviceLogService deviceLogService;
	/**
	 * Redis 设备状态服务，用于刷新设备实时状态
	 */
	private final DeviceRedisService deviceRedisService;
	/**
	 * WS分布式推送服务，用户级精准推送
	 */
	private final WsDistributedPushService wsPushService;
	/**
	 * 用户-设备关系服务，用于查询设备订阅者列表
	 */
	private final UserDeviceRelService userDeviceRelService;

	private final IotGroupUserRelService iotGroupUserRelService;

	private final StringRedisTemplate stringRedisTemplate;
	/**
	 * 设备分组服务，用于查询设备分组列表
	 */
	private final IotDevGroupService iotDevGroupService;
	@Value("${mqtt.webHookSwitch}")
	private String webHookSwitch;
	/**
	 * 直连订阅模式开关：true=遥测数据直接写入 InfluxDB；false=由 EMQX 规则引擎转发 Kafka，消费端写入
	 */
	@Value("${mqtt.directSubscribeSwitch}")
	private String directSubscribeSwitch;


	/**
	 * 构造方法注入依赖（使用 @Lazy 解决与 MqttClientManager 的循环依赖）
	 *
	 * @param mqttClientManager    MQTT 客户端管理器（延迟注入）
	 * @param deviceService        设备资产服务
	 * @param telemetryDataService    传感器数据服务
	 * @param deviceLogService     设备日志服务
	 * @param deviceRedisService     Redis 状态服务
	 * @param wsPushService          WS分布式推送服务（用户级精准推送）
	 * @param userDeviceRelService   用户-设备关系服务（查询订阅者）
	 */
	//public MqttMessageHandler(@Lazy MqttClientManager mqttClientManager,
	//						  DeviceService deviceService,
	//						  TelemetryDataService telemetryDataService,
	//						  DeviceLogService deviceLogService,
	//						  DeviceRedisService deviceRedisService,
	//						  WsDistributedPushService wsPushService,
	//						  UserDeviceRelService userDeviceRelService, @Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
	//    this.mqttClientManager = mqttClientManager;
	//    this.deviceService = deviceService;
	//    this.telemetryDataService = telemetryDataService;
	//    this.deviceLogService = deviceLogService;
	//    this.deviceRedisService = deviceRedisService;
	//    this.wsPushService = wsPushService;
	//    this.userDeviceRelService = userDeviceRelService;
	//    this.stringRedisTemplate = stringRedisTemplate;
	//}

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
				// 传感器数据主题
				handleTelemetryDataUpload(upDataMessage);
			} else if (topic.startsWith(TOPIC_CLIENT_STAUTS_PREFIX)) {
				// 若不启用 WebHook，则使用固件固定上下线机制 处理设备状态主题
				if (!"true".equals(webHookSwitch)) {
					// 设备状态主题
					if (header.getMsgType().equals(MqttConstants.ONLINE)) {
						log.info("device online: {}", topic);
						handleIotDeviceConnect(topic, upDataMessage);
					} else if (header.getMsgType().equals(MqttConstants.OFFLINE)) {
						log.info("device offline: {}", topic);
						handleIotDeviceDisconnected(topic, upDataMessage);
					}
				}
			} else {
				log.warn("Unhandled MQTT topic: {}", topic);
			}
		} catch (Exception e) {
			log.error("Failed to handle MQTT message on topic [{}]: {}", topic, e.getMessage(), e);
		}

	}

	/**
	 * 处理iot 设备心跳上报
	 * <p>
	 * 完整处理流程：
	 * <ol>
	 * 更新   iot:dev:active:{devId}      string 设备归属groupId、orgId、traceId、activeTs     ex 24h+随机时间 到期自动修改
	 * 更新 iot:{orgId}:group:dev:mems:{groupId}
	 * 更新 iot:dev:global:bucket:{n}	 * </ol>
	 * </p>
	 */
	public void handleIotHeartbeatUpload(MqttUpDataMessage message) {
		MqttMessageHeader header = message.getHeader();
		MqttMessagePayload payload = message.getPayload();
		Long timestamp = header.getTimestamp();

		if (ObjectUtils.isEmpty(header)) {
			log.error("Invalid HEART_BEAT : {}", JSONObject.toJSONString(message));
			return;
		}

		// check message type
		if (!MqttConstants.HEART_BEAT.equals(header.getMsgType())) {
			log.warn("Unexpected heartbeat msgType : {}", header.getMsgType());
			return;
		}

		String deviceId = header.getDeviceId();
		if (ObjectUtils.isEmpty(deviceId)) {
			log.error("Missing deviceId in HEART_BEAT: {}", JSONObject.toJSONString(message));
			return;
		}
		JSONObject deviceActiveInfo = deviceRedisService.getDevActiveJsonInfo(deviceId);
		deviceActiveInfo.put("activeTs", timestamp);
		if (ObjectUtils.isNotEmpty(deviceActiveInfo)){
			deviceRedisService.updateDeviceActiveAtomic(deviceId, timestamp, deviceActiveInfo.toString(), MqttConstants.IOT_DEV_ACTIVE_EX);
			// 校验消息是否是最新消息  查实时数据快照时间戳进行时间比对
			String groupId = deviceActiveInfo.getString("groupId");
			String orgId = deviceActiveInfo.getString("orgId");
			Long activeTs = deviceActiveInfo.getLong("activeTs");
			if (ObjectUtils.isNotEmpty(activeTs) && timestamp < activeTs){
				log.warn("Outdated heartbeat, ignore: {}", header.getTraceId());
				return;
			}
			deviceRedisService.dealIotHeartBeat(header, orgId, groupId);
		}
		
		log.debug("Processed heartbeat from device: {}", deviceId);
	}

	/**
	 * 处理传感器数据上报（UP_DATA 标准格式）
	 * <p>
	 * 完整处理流程：
	 * <ol>
	 *   <li>解析 header + payload 结构</li>
	 *   <li>自动注册设备（首次上报）</li>
	 *   <li>时序入库 InfluxDB（仅直连订阅模式，关闭时由 Kafka 消费端写入）</li>
	 *   <li>刷新 Redis 设备快照（iot:active:{devId}，TTL 24h）+ 维护在线数</li>
	 *   <li>组装 WebSocket 推送报文，广播给所有前端会话</li>
	 * </ol>
	 * </p>
	 */
	public void handleTelemetryDataUpload(MqttUpDataMessage message) {
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

		// 校验消息是否是最新消息  查实时数据快照时间戳进行时间比对
		String latestActiTs = deviceRedisService.getDeviceActiveInfo(deviceId);
		if (ObjectUtils.isNotEmpty(latestActiTs) && header.getTimestamp() < Long.valueOf(latestActiTs)) {
			log.warn("Outdated sensor data, ignore: {}", header.getTraceId());
			return;
		}

		log.debug("UP_DATA received from device [{}], traceId={}", deviceId, header.getTraceId());


		// 自动注册设备（首次上报时自动创建）  TODO 可优化 先查redis注册设备列表
		deviceService.autoRegister(deviceId);

		// 解析传感器状态（从 payload.sensorStatus 中获取各传感器独立状态）
		Integer deviceStatus = payload.getDeviceStatus() != null ? payload.getDeviceStatus() : SensorStatusEnum.NORMAL.getCode();
		Integer aht20Status = 2;
		Integer bmp280Status = 2;
		if (payload.getSensorStatus() != null) {
			aht20Status = payload.getSensorStatus().getAht20() != null ? payload.getSensorStatus().getAht20() : 2;
			bmp280Status = payload.getSensorStatus().getBmp280() != null ? payload.getSensorStatus().getBmp280() : 2;
		}

		// ========== 异步写入 InfluxDB ==========
		SensorData sensorData = buildSensorData(deviceId, payload, deviceStatus, aht20Status, bmp280Status);
		telemetryDataService.saveSensorData(sensorData);

		// ========== 刷新 Redis 设备快照 、维护用户在线iot设备列表、维护在线iot设备数 ==========
		// 构建完整设备数据 JSON（存入 {userId}:{deviceId}:latest，TTL 24h）
		Map deviceDataMap = buildDeviceLatestMap(deviceId, deviceStatus, aht20Status, bmp280Status, payload, header.getTimestamp());
		//deviceRedisService.updateDeviceLatestData(deviceId, deviceDataMap);

		// ========== WebSocket 用户级实时推送 ==========
		try {
			String wsMessage = buildWebSocketPushMessage(message);
			wsPushService.pushToAllUserSubcriGroup(deviceId, wsMessage);
			// 查询订阅该设备的用户列表，逐一推送
			Set<String> subscriberUserIds = userDeviceRelService.getSubscriberUserIds(deviceId);
			for (String userId : subscriberUserIds) {
				wsPushService.pushToUser(userId, "REAL_TIME_DATA", wsMessage);
			}
		} catch (Exception e) {
			log.warn("Failed to push WS message for device [{}]: {}", deviceId, e.getMessage());
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
	 * 处理 mqtt client 设备上线连接事件
	 * 主题格式:
	 *
	 * <p>
	 * 设备上线：
	 * 查出   iot:dev:active:{devId}      string 设备归属groupId、orgId、traceId、activeTs     ex 24h+随机时间 到期自动修改
	 * 查出  iot:dev:groups:{devId}   group集合，无则创建  ex 24h+随机时间 到期自动修改
	 * 查 iot:{orgId}:group:auth:{groupId} 各group的user集合 -------项目启动时加载，查不到则加载
	 * 写入 iot:{orgId}:group:dev:mems:{groupId}
	 * 写入 iot:dev:global:bucket:{n}
	 * 设备log更新
	 * redis 新增/刷新 global iot zset、用户在线iot设备列表
	 * ws 推送在线iot设备数量
	 * </p>
	 */
	public void handleIotDeviceConnect(String topic, MqttUpDataMessage message) {
		// 从主题中提取 clientId（即 device_id）
		MqttMessageHeader header = message.getHeader();
		if (ObjectUtils.isEmpty(header)) {
			log.warn("Invalid disconnected topic format: {}", topic);
			return;
		}
		String deviceId = header.getDeviceId();
		Long timestamp = header.getTimestamp();
		log.debug("Device connected event received for device: {}", deviceId);
		//// 2.比对上线时间  防止超时消费/重复消费    先查redis,查不到则设备未上线
		//deviceRedisService.getDevFromGlobalBucket(deviceId)
		//SensorData lastData = telemetryDataService.getLatestSensorDataRaw(deviceId);
		//if (ObjectUtils.isEmpty(lastData) || lastData.getReportTime().) {
		//}

		JSONObject activeInfoJson;
		//String activeInfo = stringRedisTemplate.opsForValue().get(String.format(MqttConstants.IOT_DEV_ACTIVE, deviceId));
		activeInfoJson = deviceRedisService.getDevActiveJsonInfo(deviceId);
		if (ObjectUtils.isEmpty(activeInfoJson)) return;

		// connect check
		String traceId = header.getTraceId();

		if (ObjectUtils.isNotEmpty(activeInfoJson.getString("activeTs"))
				&& Long.valueOf(activeInfoJson.getString("activeTs")) > timestamp) {
			log.debug("Device already connected recently, ignore traceId: {}", traceId);
			return;
		}

		activeInfoJson.put("activeTs", timestamp);
		deviceRedisService.updateDeviceActiveAtomic(deviceId, timestamp, activeInfoJson.toString(), MqttConstants.IOT_DEV_ACTIVE_EX);

		// 记录设备离线日志
		//String offlineContext = buildDevStateContext(deviceId, lastData);
		String logType = DeviceLogEnum.ONLINE.getCode();
		DeviceLog deviceLog = DeviceLog.builder().deviceId(deviceId)
				.logType(logType)
				.orgId(activeInfoJson.getString("orgId"))
				//.logDetail(offlineContext)
				.logTime(DateTimeUtils.utcMilliToBeijingLocal(timestamp))
				.traceId(traceId).build();
		deviceLogService.save(deviceLog);

		// query IOT_GROUP_REL
		Set<String> devRelGroupSet = deviceRedisService.getDevRelGroupSet(activeInfoJson.getString("groupId"), activeInfoJson.getString("orgId"));

		// query IOT_ORG_GROUP_AUTH
		Set<String> relGroupAuthSet = deviceRedisService.getRelGroupAuthSet(devRelGroupSet, activeInfoJson.getString("orgId"));

		// 新增/刷新 用户在线iot设备列表（纯 Redis 操作）
		long groupDevOnlineCount = deviceRedisService.iotDeviceOnline(activeInfoJson.getString("orgId"), activeInfoJson.getString("groupId"), deviceId, timestamp);

		// 构建 WS 事件并推送给订阅用户 TODO 可修改为 org 级别的广播，设计更轻便
		//Set<String> subscriberUserIds = userDeviceRelService.getSubscriberUserIds(deviceId);
		for (String userId : relGroupAuthSet) {
			WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
			wsUploadDataDTO.setType(WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode());
			WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
			WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
			deviceDTO.setDeviceId(deviceId);
			dataDTO.setGroupId(activeInfoJson.getString("groupId"));
			dataDTO.setIotDeviceOnlineCount(Integer.valueOf(String.valueOf(groupDevOnlineCount)));
			wsUploadDataDTO.setData(dataDTO);
			wsUploadDataDTO.setDevice(deviceDTO);
			wsPushService.pushToUser(userId, WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode(), wsUploadDataDTO);
		}
	}

	/**
	 * 处理 EMQX 设备断连事件
	 * 主题格式: $SYS/brokers/{node}/clients/{client_id}/disconnected
	 * <p>
	 * 设备离线时：删除 Redis 最新数据 Key + DECR 在线数 + 记录离线日志
	 * </p>
	 */
	public void handleIotDeviceDisconnected(String topic, MqttUpDataMessage message) {
		// 从主题中提取 clientId（即 device_id）
		MqttMessageHeader header = message.getHeader();
		if (ObjectUtils.isEmpty(header)) {
			log.warn("Invalid disconnected topic format: {}", topic);
			return;
		}
		String deviceId = header.getDeviceId();
		Long timestamp = header.getTimestamp();
		log.debug("Device disconnected event received for device: {}", deviceId);

		JSONObject activeInfoJson;
		activeInfoJson = deviceRedisService.getDevActiveJsonInfo(deviceId);
		if (ObjectUtils.isEmpty(activeInfoJson)) return;

		// time check
		String traceId = header.getTraceId();
		if (ObjectUtils.isNotEmpty(activeInfoJson.getString("activeTs"))
				&& Long.valueOf(activeInfoJson.getString("activeTs")) > timestamp) {
			log.debug("Device disconnect event timeout, ignore traceId: {}", traceId);
			return;
		}
		

		// 记录设备离线日志
		//String offlineContext = buildDevStateContext(deviceId, lastData);
		String logType = DeviceLogEnum.OFFLINE.getCode();
		DeviceLog deviceLog = DeviceLog.builder().deviceId(deviceId)
				.logType(logType)
				.orgId(activeInfoJson.getString("orgId"))
				//.logDetail(offlineContext)
				.logTime(DateTimeUtils.utcMilliToBeijingLocal(timestamp))
				.traceId(traceId).build();
		deviceLogService.save(deviceLog);

		// query IOT_GROUP_REL
		Set<String> devRelGroupSet = deviceRedisService.getDevRelGroupSet(activeInfoJson.getString("groupId"), activeInfoJson.getString("orgId"));

		// query IOT_ORG_GROUP_AUTH
		Set<String> relGroupAuthSet = deviceRedisService.getRelGroupAuthSet(devRelGroupSet, activeInfoJson.getString("orgId"));

		// 新增/刷新 用户在线iot设备列表（纯 Redis 操作）
		long groupDevOnlineCount = deviceRedisService.iotDeviceOffline(activeInfoJson.getString("orgId"), activeInfoJson.getString("groupId"), deviceId, timestamp);

		// 构建 WS 事件并推送给订阅用户 TODO 可修改为 org 级别的广播，设计更轻便
		//Set<String> subscriberUserIds = userDeviceRelService.getSubscriberUserIds(deviceId);
		for (String userId : relGroupAuthSet) {
			WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
			wsUploadDataDTO.setType(WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode());
			WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
			WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
			deviceDTO.setDeviceId(deviceId);
			dataDTO.setGroupId(activeInfoJson.getString("groupId"));
			dataDTO.setIotDeviceOnlineCount(Integer.valueOf(String.valueOf(groupDevOnlineCount)));
			wsUploadDataDTO.setData(dataDTO);
			wsUploadDataDTO.setDevice(deviceDTO);
			wsPushService.pushToUser(userId, WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode(), wsUploadDataDTO);
		}

	}

	/**
     * 构建树形结构
     * @param allGroupList 当前租户下全部分组列表
     * @return 返回根节点集合
     */
    public static List<IotDevGroup> buildGroupTree(List<IotDevGroup> allGroupList) {
        if(CollectionUtils.isEmpty(allGroupList)){
            return Collections.emptyList();
        }
        // groupId -> entity map
        Map<String, IotDevGroup> groupMap = allGroupList.stream()
                .collect(Collectors.toMap(IotDevGroup::getGroupId, g -> g));

        List<IotDevGroup> rootList = new ArrayList<>();
        for (IotDevGroup group : allGroupList) {
            String parentId = group.getParentGroupId();
            // parentGroupId为null 就是根节点
            if(ObjectUtils.isEmpty(parentId)){
                rootList.add(group);
            }else{
                IotDevGroup parent = groupMap.get(parentId);
                if(parent != null){
                    parent.getChildren().add(group);
                }
            }
        }
        // 每层内部按sortNum升序排序
        sortRecursive(rootList);
        return rootList;
    }

    /**
     * 递归按照sortNum排序
     */
    private static void sortRecursive(List<IotDevGroup> nodeList){
        if(CollectionUtils.isEmpty(nodeList)){
            return;
        }
        nodeList.sort(Comparator.comparingInt(IotDevGroup::getSortNum));
        for(IotDevGroup node : nodeList){
            sortRecursive(node.getChildren());
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
	private String buildDevStateContext(String deviceId, SensorData lastData) {
		try {
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
	 * 根据 UP_DATA payload 构建 SensorData 实体对象（直连订阅模式直写 InfluxDB 用）
	 *
	 * @param deviceId     设备ID（从 header 中提取）
	 * @param payload      UP_DATA 消息体
	 * @param sensorStatus 传感器整体状态码
	 * @param aht20Status  AHT20 温湿度传感器状态码
	 * @param bmp280Status BMP280 气压传感器状态码
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
	private HashMap<String, Object> buildDeviceLatestMap(String deviceId, Integer deviceStatus,
														 Integer aht20Status, Integer bmp280Status,
														 MqttMessagePayload body, Long deviceTimestamp) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("deviceId", deviceId);
		map.put("deviceStatus", deviceStatus);
		map.put("aht20Status", aht20Status);
		map.put("bmp280Status", bmp280Status);
		map.put("timestamp", deviceTimestamp != null ? deviceTimestamp : System.currentTimeMillis());

		// 填充 envData 字段
		MqttUpEnvData env = body.getEnvData(); // Changed from body.getEnvData() to payload.getEnvData()
		if (env != null) {
			map.put("tempAht", formatBigDecimal(env.getTempAht()));
			map.put("tempBmp", formatBigDecimal(env.getTempBmp()));
			map.put("humidity", formatBigDecimal(env.getHumidity()));
			map.put("pressureHpa", formatBigDecimal(env.getPressureHpa()));
			map.put("altitude", formatBigDecimal(env.getAltitude()));
		}
		return map;
	}

	/**
	 * 格式化BigDecimal字段
	 */
	private String formatBigDecimal(BigDecimal value) {
		if (value == null) return null;
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
		//long onlineCount = deviceRedisService.getUserIotDeviceOnlineCount();
		MqttUpEnvData env = payload.getEnvData();
		if (env != null) {
			data.put("tempAht", formatBigDecimal(env.getTempAht()));
			data.put("humidity", formatBigDecimal(env.getHumidity()));
			data.put("pressureHpa", formatBigDecimal(env.getPressureHpa()));
			data.put("altitude", formatBigDecimal(env.getAltitude()));
			data.put("tempBmp", formatBigDecimal(env.getTempBmp()));
		}
		//data.put("onlineCount", onlineCount);
		message.put("data", data);

		// device 部分：设备级详细信息
		JSONObject device = new JSONObject();
		device.put("deviceId", deviceId);
		device.put("deviceStatus", deviceStatus);
		device.put("aht20Status", aht20Status);
		device.put("bmp280Status", bmp280Status);

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
		//mqttClientManager.publish(topic, jsonPayload, 1);
		log.info("Published DOWN_CMD to device [{}], cmdCode={}, requestId={}", deviceId, cmdCode, requestId);

		return requestId;
	}
}
