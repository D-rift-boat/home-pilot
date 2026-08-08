package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Command query request")
public class CommandQueryReqDTO extends BaseReqDTO {

    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "Target device ID", example = "esp32s3_001")
    private String deviceId;
}
