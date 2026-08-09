package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 根据设备业务标识查询设备请求 DTO
 * <p>通过 POST /api/device/getByDeviceId 接口提交，使用 device_id（如 esp32-S3-001）查询</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "根据设备标识查询设备请求")
public class DeviceGetByDeviceIdReqDTO extends BaseReqDTO {

    /** 设备唯一业务标识，如 "esp32-S3-001" */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "设备唯一业务标识", example = "esp32s3_001")
    private String deviceId;
}
