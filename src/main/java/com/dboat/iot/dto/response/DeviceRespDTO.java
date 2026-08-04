package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Device info response")
public class DeviceRespDTO {

    @Schema(description = "Internal ID")
    private String id;

    @Schema(description = "Unique device identifier")
    private String deviceId;

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

    @Schema(description = "Created time")
    private LocalDateTime createTime;

    @Schema(description = "Updated time")
    private LocalDateTime updateTime;
}
