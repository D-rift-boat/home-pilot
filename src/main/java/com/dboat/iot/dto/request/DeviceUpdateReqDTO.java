package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Device update request")
public class DeviceUpdateReqDTO {

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
