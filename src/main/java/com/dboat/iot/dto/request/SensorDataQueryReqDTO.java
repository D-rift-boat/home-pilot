package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Schema(description = "Sensor data query request")
public class SensorDataQueryReqDTO {

    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "Device ID", example = "esp32s3_001")
    private String deviceId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Start time", example = "2025-01-01 00:00:00")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "End time", example = "2025-12-31 23:59:59")
    private LocalDateTime endTime;
}
