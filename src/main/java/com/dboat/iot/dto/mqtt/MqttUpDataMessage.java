package com.dboat.iot.dto.mqtt;

import lombok.Data;

/**
 * MQTT 数据上报消息 DTO —— 对应设备上行消息类型 UP_DATA
 * <p>
 * 标准 header + payload 结构，设备通过 MQTT 主题 iot/sensor/upload/+ 上报传感器遥测数据。
 * </p>
 *
 * @author dboat
 */
@Data
public class MqttUpDataMessage {

    /** 消息头，包含路由与元数据 */
    private MqttMessageHeader header;

    /** 消息体，包含业务数据 */
    private MqttMessagePayload payload;
}
