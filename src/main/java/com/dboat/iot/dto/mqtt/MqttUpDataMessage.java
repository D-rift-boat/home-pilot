package com.dboat.iot.dto.mqtt;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

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
    private Header header;

    /** 消息体，包含业务数据 */
    private Payload payload;

    /**
     * 消息头
     */
    @Data
    public static class Header {
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

    /**
     * 消息体
     */
    @Data
    public static class Payload {
        /** 整机设备运行总状态 */
        private Integer deviceStatus;
        /** 各传感器独立状态 */
        private SensorStatus sensorStatus;
        /** 环境监测核心采集数据 */
        private EnvData envData;
        /** 扩展预留字段 */
        private Map<String, Object> extend;
    }

    /**
     * 各传感器独立状态
     */
    @Data
    public static class SensorStatus {
        /** AHT20 温湿度传感器状态码 */
        private Integer aht20;
        /** BMP280 气压传感器状态码 */
        private Integer bmp280;
    }

    /**
     * 环境监测核心采集数据
     */
    @Data
    public static class EnvData {
        /** AHT20 采集温度（°C） */
        private BigDecimal tempAht;
        /** BMP280 采集温度（°C） */
        private BigDecimal tempBmp;
        /** 环境湿度（%RH） */
        private BigDecimal humidity;
        /** 大气压强（hPa） */
        private BigDecimal pressureHpa;
        /** 海拔高度（米） */
        private BigDecimal altitude;
    }
}
