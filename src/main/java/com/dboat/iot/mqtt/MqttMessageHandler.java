package com.dboat.iot.mqtt;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.entity.SensorData;
import com.dboat.iot.service.DeviceService;
import com.dboat.iot.service.SensorDataService;
import com.dboat.iot.utils.JsonUtils;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private static final String TOPIC_DATA_UPLOAD = "device/data/upload";
    private static final String TOPIC_COMMAND_PREFIX = "device/command/";

    private final MqttClientManager mqttClientManager;
    private final DeviceService deviceService;
    private final SensorDataService sensorDataService;

    public MqttMessageHandler(@Lazy MqttClientManager mqttClientManager,
                               DeviceService deviceService,
                               SensorDataService sensorDataService) {
        this.mqttClientManager = mqttClientManager;
        this.deviceService = deviceService;
        this.sensorDataService = sensorDataService;
    }

    /**
     * Handle incoming MQTT messages
     */
    public void handleMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.info("Received MQTT message on topic [{}]: {}", topic, payload);

        try {
            if (topic.equals(TOPIC_DATA_UPLOAD)) {
                handleDataUpload(payload);
            } else {
                log.warn("Unhandled MQTT topic: {}", topic);
            }
        } catch (Exception e) {
            log.error("Failed to handle MQTT message on topic [{}]: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Handle sensor data upload from device
     */
    private void handleDataUpload(String payload) {
        JSONObject json = JsonUtils.parseObject(payload);
        if (json == null) {
            log.error("Invalid JSON payload: {}", payload);
            return;
        }

        String deviceId = json.getString("device_id");
        if (deviceId == null || deviceId.isEmpty()) {
            log.error("Missing device_id in payload: {}", payload);
            return;
        }

        // Auto-register device if not exists
        deviceService.autoRegister(deviceId);

        // Parse sensor data
        SensorData sensorData = new SensorData();
        sensorData.setDeviceId(deviceId);

        if (json.containsKey("temp")) {
            sensorData.setTemp(json.getBigDecimal("temp"));
        }
        if (json.containsKey("humi")) {
            sensorData.setHumi(json.getBigDecimal("humi"));
        }
        if (json.containsKey("press")) {
            sensorData.setPress(json.getLong("press"));
        }

        // Use device timestamp or current time
        if (json.containsKey("ts")) {
            sensorData.setReportTime(Instant.ofEpochMilli(json.getLong("ts")));
        } else {
            sensorData.setReportTime(Instant.now());
        }

        // Save to InfluxDB
        sensorDataService.saveSensorData(sensorData);
        log.info("Processed sensor data from device: {}", deviceId);
    }

    /**
     * Publish a command to a device's dedicated topic
     */
    public void publishCommand(String deviceId, String command) {
        String topic = TOPIC_COMMAND_PREFIX + deviceId;
        JSONObject payload = new JSONObject();
        payload.put("command", command);
        payload.put("ts", System.currentTimeMillis());
        mqttClientManager.publish(topic, payload.toJSONString(), 1);
        log.info("Published command to device [{}]: {}", deviceId, command);
    }
}
