package com.dboat.iot.dto.kafka;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 原始传感器 kafka数据
 */
@Data
public class SensorRawKafkaMsg {
    /** 对应你实体 deviceId */
    private String deviceId;

    private BigDecimal temperatureAht;
    private BigDecimal temperatureBmp;
    private BigDecimal humidity;
    private BigDecimal pressureHpa;
    private BigDecimal altitudeM;

    private Integer sensorStatus;
    private Integer aht20Status;
    private Integer bmp280Status;

    /** 设备上报毫秒时间戳 */
    private Long reportTs;
}
