package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备创建请求 DTO
 * <p>用于手动注册新设备，通过 POST /api/device/create 接口提交</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备创建请求")
public class DeviceCreateReqDTO extends BaseReqDTO {

    /** 设备唯一标识（业务ID），如 "esp32-S3-001" */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "设备唯一标识", example = "esp32s3_001")
    private String deviceId;

    /** 设备名称（可读描述） */
    @Schema(description = "设备名称", example = "Living Room Sensor")
    private String deviceName;

    /** 设备型号，如 "ESP32-S3" */
    @Schema(description = "设备型号", example = "ESP32-S3")
    private String deviceModel;

    /** 固件版本号 */
    @Schema(description = "固件版本", example = "1.0.0")
    private String firmwareVersion;

    /** 设备安装位置 */
    @Schema(description = "安装位置", example = "Living Room")
    private String location;
}
