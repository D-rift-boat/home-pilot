package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Device create request")
public class DeviceCreateReqDTO {

    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "Unique device identifier", example = "esp32s3_001")
    private String deviceId;

    @Schema(description = "Device name", example = "Living Room Sensor")
    private String deviceName;

    @Schema(description = "Device model", example = "ESP32-S3")
    private String deviceModel;

    @Schema(description = "Firmware version", example = "1.0.0")
    private String firmwareVersion;

    @Schema(description = "Installation location", example = "Living Room")
    private String location;
}
