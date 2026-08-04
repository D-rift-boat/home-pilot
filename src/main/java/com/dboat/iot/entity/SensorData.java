package com.dboat.iot.entity;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Measurement(name = "sensor")
public class SensorData {

    @Column(tag = true)
    private String deviceId;

    @Column
    private BigDecimal temp;

    @Column
    private BigDecimal humi;

    @Column
    private Long press;

    @Column(timestamp = true)
    private Instant reportTime;
}
