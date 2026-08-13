package com.dboat.iot.dto.mqtt;

import lombok.Data;

import java.util.Map;

/**
 * MQTT 上行消息体，包含设备上报的业务数据
 *
 * @author dboat
 */
@Data
public class MqttMessagePayload {
    /** 整机设备运行总状态 */
    private Integer deviceStatus;
    /** 各传感器独立状态 */
    private MqttUpSensorState sensorStatus;
    /** 环境监测核心采集数据 */
    private MqttUpEnvData envData;
    /** 扩展预留字段 */
    private Map<String, Object> extend;
}
