package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Device list request")
public class DeviceListReqDTO extends BaseReqDTO {

    @Schema(description = "Device ID (fuzzy match)", example = "esp32")
    private String deviceId;

    @Schema(description = "Device name (fuzzy match)", example = "Living Room")
    private String deviceName;

    @Schema(description = "Device model (exact match)", example = "ESP32-S3")
    private String deviceModel;

    @Schema(description = "Device status: 0=offline, 1=online, 2=abnormal")
    private Integer status;

    @Schema(description = "Page number", example = "1", defaultValue = "1")
    private Integer pageNum = 1;

    @Schema(description = "Page size", example = "10", defaultValue = "10")
    private Integer pageSize = 10;
}
