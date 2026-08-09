package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备删除请求 DTO
 * <p>用于逻辑删除设备（通过 @TableLogic 标记），通过 POST /api/device/delete 接口提交</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备删除请求")
public class DeviceDeleteReqDTO extends BaseReqDTO {

    /** 设备内部主键ID（UUID） */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "设备内部主键ID", example = "1234567890")
    private String id;
}
