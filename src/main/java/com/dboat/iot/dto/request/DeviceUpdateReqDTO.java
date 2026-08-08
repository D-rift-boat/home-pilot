package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Device update request")
public class DeviceUpdateReqDTO extends BaseReqDTO {

    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "Device internal ID", example = "1234567890")
    private String id;

    @Schema(description = "Device name")
    private String deviceName;

    @Schema(description = "Device model")
    private String deviceModel;

    @Schema(description = "Firmware version")
    private String firmwareVersion;

    @Schema(description = "Installation location")
    private String location;

    @Schema(description = "Device status: 0=offline, 1=online, 2=abnormal")
    private Integer status;
}
