package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Device get by ID request")
public class DeviceGetByIdReqDTO extends BaseReqDTO {

    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "Device internal ID", example = "1234567890")
    private String id;
}
