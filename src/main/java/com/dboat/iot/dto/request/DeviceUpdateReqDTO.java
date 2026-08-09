package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备更新请求 DTO
 * <p>用于更新设备静态属性信息，通过 POST /api/device/update 接口提交</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备更新请求")
public class DeviceUpdateReqDTO extends BaseReqDTO {

    /** 设备内部主键ID（UUID） */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "设备内部主键ID", example = "1234567890")
    private String id;

    /** 设备名称 */
    @Schema(description = "设备名称")
    private String deviceName;

    /** 设备型号 */
    @Schema(description = "设备型号")
    private String deviceModel;

    /** 固件版本号 */
    @Schema(description = "固件版本")
    private String firmwareVersion;

    /** 安装位置 */
    @Schema(description = "安装位置")
    private String location;

    /** 所属产品ID */
    @Schema(description = "所属产品ID")
    private String productId;
}
