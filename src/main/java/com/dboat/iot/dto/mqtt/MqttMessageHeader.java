package com.dboat.iot.dto.mqtt;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 消息头
 */
@Data
public class MqttMessageHeader {
    /** 消息类型，固定为 "UP_DATA" */
    private String msgType;
    /** 消息追踪唯一ID（UUID），用于链路追踪 */
    private String traceId;
    /** 设备唯一标识 */
    private String deviceId;
    /** 设备上报时间戳（毫秒级） */
    private Long timestamp;
    /** 产品标识，如 "env_monitor_01" */
    private String productKey;
    /** 协议版本号 */
    private String version;
}
