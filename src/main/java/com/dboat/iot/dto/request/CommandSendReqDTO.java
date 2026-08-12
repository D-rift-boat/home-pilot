package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 设备指令下发请求 DTO
 * <p>通过 POST /api/command/send 接口提交，向指定设备发送标准 DOWN_CMD 格式指令</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备指令下发请求")
public class CommandSendReqDTO extends BaseReqDTO {

    /** 目标设备唯一标识 */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "目标设备标识", example = "esp32-S3-001")
    private String deviceId;

    /**
     * 指令编码，统一枚举：
     * <ul>
     *   <li>device_restart：设备重启</li>
     *   <li>sensor_calibrate：传感器校准</li>
     *   <li>light_switch：外接灯光开关</li>
     * </ul>
     */
    @NotBlank(message = "Command code cannot be empty")
    @Schema(description = "指令编码", example = "device_restart")
    private String cmdCode;

    /** 指令参数，不同 cmdCode 对应不同的参数结构 */
    @Schema(description = "指令参数", example = "{}")
    private Map<String, Object> params;

    /** 指令超时时间（毫秒），默认 5000ms */
    @Schema(description = "指令超时时间(ms)", example = "5000")
    private Long timeout;
}
