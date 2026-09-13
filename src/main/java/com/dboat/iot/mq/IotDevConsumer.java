package com.dboat.iot.mq;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.common.constants.KafkaTopicConstants;
import com.dboat.iot.common.constants.MqttConstants;
import com.dboat.iot.dto.kafka.SensorRawKafkaMsg;
import com.dboat.iot.dto.mqtt.*;
import com.dboat.iot.mqtt.MqttMessageHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 传感器遥测数据 Kafka 消费者
 * <p>
 * 消费 EMQX 规则引擎转发至遥测 Topic 的实时数据，
 * 复用 {@link MqttMessageHandler#handleTelemetryDataUpload} 完整处理链路，
 * 与直连订阅模式保持一致的业务语义：
 * <ul>
 *   <li>遥测数据写入 InfluxDB 时序数据库</li>
 *   <li>刷新 Redis 设备实时快照</li>
 *   <li>WebSocket 用户级实时推送</li>
 * </ul>
 * 兼容两种消息格式：
 * <ul>
 *   <li>EMQX 规则引擎直接转发的设备原始报文（header + payload 标准格式）</li>
 *   <li>规则引擎加工后的扁平化报文（{@link SensorRawKafkaMsg} 结构，内部统一转换为标准报文）</li>
 * </ul>
 * 仅在直连订阅模式关闭（mqtt.directSubscribeSwitch=false）时启动，
 * 避免与直连订阅链路重复处理。
 * 单条消息解析失败仅记录日志并跳过（毒消息不阻塞整批消费）；
 * 业务处理失败时抛出异常，由 {@link com.dboat.iot.config.KafkaConfig}
 * 中配置的错误处理器重试，重试耗尽后转入死信 Topic。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Component
public class IotDevConsumer {

	/**
	 * MQTT 消息处理器（复用其遥测数据完整处理链路：InfluxDB + Redis 快照 + WS 推送）
	 */
	@Resource
	private MqttMessageHandler mqttMessageHandler;


	@Value("${kafka.topic.iot-device-status-topic}")
	private String iotDeviceStatusTopic;

	/**
	 * 批量消费遥测消息，复用 MQTT 上行处理链路逐条处理
	 * <p>
	 * 批量消费 + 手动提交（由 spring.kafka.listener.type=batch、
	 * ack-mode=manual_immediate 配置驱动）；
	 * autoStartup 绑定直连订阅开关：仅直连订阅模式关闭时启动消费。
	 * </p>
	 *
	 * @param records 本批次拉取到的消息集合
	 * @param ack     手动偏移量提交句柄
	 */
	@KafkaListener(topics = "${" + KafkaTopicConstants.IOT_DEVICE_STATUS_TOPIC + "}",
			autoStartup = "#{!'true'.equals('${mqtt.directSubscribeSwitch}') && !'true'.equals('${mqtt.webHookSwitch}')}")
	public void onIotDeviceStatus(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
		if (records == null || records.isEmpty()) {
			return;
		}
		log.info("Consumed {} telemetry msg(s) from kafka", records.size());

		// 逐条解析并复用 MQTT 上行处理链路（解析失败仅跳过该条，不阻塞整批）
		for (ConsumerRecord<String, String> record : records) {
			try {
				MqttUpDataMessage msg = parseToUpDataMessage(record.value());
				if (msg == null) {
					log.warn("Invalid telemetry msg, skip. offset={}, value={}",
							record.offset(), record.value());
					continue;
				}
				// 设备状态主题
				// 若不启用 WebHook，则使用固件固定上下线机制 处理设备状态主题
				if (msg.getHeader().getMsgType().equals(MqttConstants.ONLINE)) {
					log.info("device online: {}", msg.getHeader().getDeviceId());
					mqttMessageHandler.handleIotDeviceConnect(iotDeviceStatusTopic, msg);
				} else if (msg.getHeader().getMsgType().equals(MqttConstants.OFFLINE)) {
					log.info("device offline: {}", msg.getHeader().getDeviceId());
					mqttMessageHandler.handleIotDeviceDisconnected(iotDeviceStatusTopic, msg);
				}

			} catch (Exception e) {
				log.error("Failed to handle telemetry msg, skip. offset={}, value={}",
						record.offset(), record.value(), e);
			}
		}

		// 处理完毕后手动提交偏移量
		ack.acknowledge();
	}

	/**
	 * 批量消费遥测消息，复用 MQTT 上行处理链路逐条处理
	 * <p>
	 * 批量消费 + 手动提交（由 spring.kafka.listener.type=batch、
	 * ack-mode=manual_immediate 配置驱动）；
	 * autoStartup 绑定直连订阅开关：仅直连订阅模式关闭时启动消费。
	 * </p>
	 *
	 * @param records 本批次拉取到的消息集合
	 * @param ack     手动偏移量提交句柄
	 */
	@KafkaListener(topics = "${" + KafkaTopicConstants.IOT_HEARTBEAT_TOPIC + "}")
	public void onIotHeartBeatBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
		if (records == null || records.isEmpty()) {
			return;
		}
		log.info("Consumed {} telemetry msg(s) from kafka", records.size());

		// 逐条解析并复用 MQTT 上行处理链路（解析失败仅跳过该条，不阻塞整批）
		for (ConsumerRecord<String, String> record : records) {
			try {
				MqttUpDataMessage msg = parseToUpDataMessage(record.value());
				if (ObjectUtils.isEmpty(msg)) {
					log.warn("Invalid telemetry msg, skip. offset={}, value={}",
							record.offset(), record.value());
					continue;
				}
				mqttMessageHandler.handleIotHeartbeatUpload(msg);
			} catch (Exception e) {
				log.error("Failed to handle telemetry msg, skip. offset={}, value={}",
						record.offset(), record.value(), e);
			}
		}

		// 处理完毕后手动提交偏移量
		ack.acknowledge();
	}

	/**
	 * 解析 Kafka 遥测消息为标准上行报文（自动识别报文格式）
	 * <p>
	 * 含 header 字段 → 设备原始报文（header + payload 标准格式），仅处理 UP_DATA 类型；
	 * 否则 → 规则引擎加工后的扁平化报文（{@link SensorRawKafkaMsg} 结构），统一转换为标准报文。
	 * </p>
	 *
	 * @param value Kafka 消息体（JSON 字符串）
	 * @return 标准上行报文，非遥测数据或格式非法时返回 null
	 */
	private MqttUpDataMessage parseToUpDataMessage(String value) {
		JSONObject json = JSONObject.parseObject(value);
		if (json == null) {
			return null;
		}
		if (json.containsKey("header")) {
			// EMQX 规则引擎直接转发的设备原始报文
			MqttUpDataMessage msg = JSONObject.parseObject(value, MqttUpDataMessage.class);
			if (msg == null || msg.getHeader() == null || msg.getPayload() == null) {
				return null;
			}
			return msg;
		}
		// 规则引擎加工后的扁平化报文 → 转换为标准报文
		SensorRawKafkaMsg rawMsg = JSONObject.parseObject(value, SensorRawKafkaMsg.class);
		return rawMsg != null ? convertFromRawKafkaMsg(rawMsg) : null;
	}


	///**
	// * 解析 Kafka 遥测消息为标准上行报文（自动识别报文格式）
	// * <p>
	// * 含 header 字段 → 设备原始报文（header + payload 标准格式），仅处理 UP_DATA 类型；
	// * 否则 → 规则引擎加工后的扁平化报文（{@link SensorRawKafkaMsg} 结构），统一转换为标准报文。
	// * </p>
	// *
	// * @param value Kafka 消息体（JSON 字符串）
	// * @return 标准上行报文，非遥测数据或格式非法时返回 null
	// */
	//private MqttUpDataMessage parseToHeartBeatMessage(String value) {
	//	JSONObject json = JSONObject.parseObject(value);
	//	if (json == null) {
	//		return null;
	//	}
	//	if (json.containsKey("header")) {
	//		// EMQX 规则引擎直接转发的设备原始报文
	//		MqttUpDataMessage msg = JSONObject.parseObject(value, MqttUpDataMessage.class);
	//		if (msg == null || msg.getHeader() == null || msg.getPayload() == null) {
	//			return null;
	//		}
	//		// 仅处理遥测数据（上下线等状态事件不走本链路）
	//		if (!"UP_DATA".equals(msg.getHeader().getMsgType())) {
	//			return null;
	//		}
	//		return msg;
	//	}
	//	// 规则引擎加工后的扁平化报文 → 转换为标准报文
	//	SensorRawKafkaMsg rawMsg = JSONObject.parseObject(value, SensorRawKafkaMsg.class);
	//	return rawMsg != null ? convertFromRawKafkaMsg(rawMsg) : null;
	//}

	/**
	 * 扁平化遥测报文转标准上行报文（复用 MQTT 处理链路）
	 * <p>报文字段映射：deviceId/状态码 → header + payload，环境数据 → envData</p>
	 *
	 * @param rawMsg 扁平化遥测消息
	 * @return 标准上行报文，缺少 deviceId 时返回 null
	 */
	private MqttUpDataMessage convertFromRawKafkaMsg(SensorRawKafkaMsg rawMsg) {
		if (rawMsg.getDeviceId() == null || rawMsg.getDeviceId().isEmpty()) {
			return null;
		}

		// 构建 header（补充 msgType 与 traceId，便于复用处理链路中的校验与链路追踪）
		MqttMessageHeader header = new MqttMessageHeader();
		header.setMsgType("UP_DATA");
		header.setDeviceId(rawMsg.getDeviceId());
		header.setTimestamp(rawMsg.getReportTs() != null ? rawMsg.getReportTs() : System.currentTimeMillis());
		header.setTraceId(UUID.randomUUID().toString());

		// 构建 payload
		MqttMessagePayload payload = new MqttMessagePayload();
		payload.setDeviceStatus(rawMsg.getSensorStatus());

		MqttUpSensorState sensorState = new MqttUpSensorState();
		sensorState.setAht20(rawMsg.getAht20Status());
		sensorState.setBmp280(rawMsg.getBmp280Status());
		payload.setSensorStatus(sensorState);

		MqttUpEnvData envData = new MqttUpEnvData();
		envData.setTempAht(rawMsg.getTemperatureAht());
		envData.setTempBmp(rawMsg.getTemperatureBmp());
		envData.setHumidity(rawMsg.getHumidity());
		envData.setPressureHpa(rawMsg.getPressureHpa());
		envData.setAltitude(rawMsg.getAltitudeM());
		payload.setEnvData(envData);

		MqttUpDataMessage msg = new MqttUpDataMessage();
		msg.setHeader(header);
		msg.setPayload(payload);
		return msg;
	}
}
