package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备指令下发请求 DTO
 * <p>通过 POST /api/command/send 接口提交，向指定设备发送控制指令</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备指令下发请求")
public class CommandSendReqDTO extends BaseReqDTO {

    /** 目标设备唯一标识 */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "目标设备标识", example = "esp32s3_001")
    private String deviceId;

    /** 指令内容，如 "set_temp:25"、"restart" */
    @NotBlank(message = "Command cannot be empty")
    @Schema(description = "指令内容", example = "set_temp:25")
    private String command;
}
