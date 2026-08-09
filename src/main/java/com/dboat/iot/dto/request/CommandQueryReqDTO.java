package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备指令列表查询请求 DTO
 * <p>通过 POST /api/command/listByDevice 接口提交，查询指定设备的所有指令记录</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备指令列表查询请求")
public class CommandQueryReqDTO extends BaseReqDTO {

    /** 目标设备唯一标识 */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "目标设备标识", example = "esp32s3_001")
    private String deviceId;
}
