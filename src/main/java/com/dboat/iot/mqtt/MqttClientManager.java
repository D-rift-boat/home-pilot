package com.dboat.iot.mqtt;

import com.dboat.iot.config.MqttConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttClientManager {

    private static final Logger log = LoggerFactory.getLogger(MqttClientManager.class);

    private final MqttConfig mqttConfig;
    private final MqttConnectOptions mqttConnectOptions;
    private final MqttMessageHandler messageHandler;

    private MqttClient mqttClient;

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
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    messageHandler.handleMessage(topic, message);
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
        String[] topics = mqttConfig.getSubscribeTopics();
        int[] qosLevels = mqttConfig.getSubscribeQos();
        if (topics != null && topics.length > 0) {
            try {
                if (qosLevels == null || qosLevels.length != topics.length) {
                    qosLevels = new int[topics.length];
                    for (int i = 0; i < qosLevels.length; i++) {
                        qosLevels[i] = 1;
                    }
                }
                mqttClient.subscribe(topics, qosLevels);
                log.info("Subscribed to MQTT topics: {}", String.join(", ", topics));
            } catch (MqttException e) {
                log.error("Failed to subscribe MQTT topics: {}", e.getMessage(), e);
            }
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
