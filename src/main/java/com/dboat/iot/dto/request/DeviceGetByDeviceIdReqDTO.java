package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Device get by device ID request")
public class DeviceGetByDeviceIdReqDTO extends BaseReqDTO {

    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "Unique device identifier", example = "esp32s3_001")
    private String deviceId;
}
