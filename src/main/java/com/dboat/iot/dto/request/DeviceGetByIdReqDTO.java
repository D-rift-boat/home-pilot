package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 根据内部主键ID查询设备请求 DTO
 * <p>通过 POST /api/device/getById 接口提交</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "根据ID查询设备请求")
public class DeviceGetByIdReqDTO extends BaseReqDTO {

    /** 设备内部主键ID（UUID） */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "设备内部主键ID", example = "1234567890")
    private String id;
}
