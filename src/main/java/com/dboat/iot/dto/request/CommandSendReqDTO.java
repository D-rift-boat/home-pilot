package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Device command send request")
public class CommandSendReqDTO {

    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "Target device ID", example = "esp32s3_001")
    private String deviceId;

    @NotBlank(message = "Command cannot be empty")
    @Schema(description = "Command content", example = "set_temp:25")
    private String command;
}
