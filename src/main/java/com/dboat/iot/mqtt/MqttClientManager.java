package com.dboat.iot.mqtt;

import com.dboat.iot.config.MqttConfig;
import com.dboat.iot.service.DeviceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MqttClientManager {

	private static final Logger log = LoggerFactory.getLogger(MqttClientManager.class);

	/**
	 * EMQX 设备断连事件主题（通配符订阅所有节点的所有客户端断连）
	 */
	private static final String EMQX_DISCONNECT_TOPIC = "$SYS/brokers/+/clients/+/disconnected";

	private final MqttConfig mqttConfig;
	private final MqttConnectOptions mqttConnectOptions;
	private final MqttMessageHandler messageHandler;

	private MqttClient mqttClient;

	/** 直连订阅模式开关：true=遥测数据直接写入 InfluxDB；false=由 EMQX 规则引擎转发 Kafka，消费端写入 */
	@Value("${mqtt.directSubscribeSwitch}")
	private String directSubscribeSwitch;

	@Resource
	private RedisTemplate<String, Object> redisTemplate;

	public MqttClientManager(MqttConfig mqttConfig,
							 MqttConnectOptions mqttConnectOptions,
							 MqttMessageHandler messageHandler) {
		this.mqttConfig = mqttConfig;
		this.mqttConnectOptions = mqttConnectOptions;
		this.messageHandler = messageHandler;
	}

	@PostConstruct
	public void init() {
		try {
			mqttClient = new MqttClient(mqttConfig.getBrokerUrl(),
					mqttConfig.getClientId(), new MemoryPersistence());

			mqttClient.setCallback(new MqttCallbackExtended() {
				@Override
				public void connectComplete(boolean reconnect, String serverURI) {
					log.info("MQTT connected to {} (reconnect={})", serverURI, reconnect);
					subscribeTopics();
					//re
					redisTemplate.opsForValue().set("mqtt:connected", true);
				}

				@Override
				public void connectionLost(Throwable cause) {
					log.warn("MQTT connection lost: {}", cause.getMessage());
				}

				@Override
				public void messageArrived(String topic, MqttMessage message) {
					if ("true".equals(directSubscribeSwitch)) {
						messageHandler.handleMessage(topic, message);
					}
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {
					log.debug("MQTT message delivered");
				}
			});

			mqttClient.connect(mqttConnectOptions);
		} catch (MqttException e) {
			log.error("Failed to initialize MQTT client: {}", e.getMessage(), e);
		}
	}

	private void subscribeTopics() {
		// 合并配置中的订阅主题和系统事件主题
		List<String> allTopics = new ArrayList<>();
		List<Integer> allQos = new ArrayList<>();

		// 配置中的业务主题
		String[] topics = mqttConfig.getSubscribeTopics();
		int[] qosLevels = mqttConfig.getSubscribeQos();
		if (topics != null && topics.length > 0) {
			for (int i = 0; i < topics.length; i++) {
				allTopics.add(topics[i]);
				allQos.add(qosLevels != null && i < qosLevels.length ? qosLevels[i] : 1);
			}
		}

		// EMQX 系统事件主题（设备断连感知）
		allTopics.add(EMQX_DISCONNECT_TOPIC);
		allQos.add(1);

		try {
			String[] topicArr = allTopics.toArray(new String[0]);
			int[] qosArr = allQos.stream().mapToInt(Integer::intValue).toArray();
			mqttClient.subscribe(topicArr, qosArr);
			log.info("Subscribed to MQTT topics: {}", String.join(", ", topicArr));
		} catch (MqttException e) {
			log.error("Failed to subscribe MQTT topics: {}", e.getMessage(), e);
		}
	}

	/**
	 * Publish a message to a topic
	 */
	public void publish(String topic, String payload, int qos) {
		try {
			MqttMessage message = new MqttMessage(payload.getBytes());
			message.setQos(qos);
			mqttClient.publish(topic, message);
			log.debug("Published MQTT message to topic [{}]: {}", topic, payload);
		} catch (MqttException e) {
			log.error("Failed to publish MQTT message to topic [{}]: {}", topic, e.getMessage(), e);
			throw new RuntimeException("MQTT publish failed", e);
		}
	}

	public boolean isConnected() {
		return mqttClient != null && mqttClient.isConnected();
	}

	@PreDestroy
	public void destroy() {
		if (mqttClient != null && mqttClient.isConnected()) {
			try {
				mqttClient.disconnect();
				log.info("MQTT client disconnected");
			} catch (MqttException e) {
				log.error("Failed to disconnect MQTT client: {}", e.getMessage(), e);
			}
		}
	}
}
