package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Schema(description = "Sensor data response")
public class SensorDataRespDTO {

    @Schema(description = "Device ID")
    private String deviceId;

    @Schema(description = "Temperature")
    private BigDecimal temp;

    @Schema(description = "Humidity")
    private BigDecimal humi;

    @Schema(description = "Air pressure (Pa)")
    private Long press;

    @Schema(description = "Report time")
    private Instant reportTime;
}
