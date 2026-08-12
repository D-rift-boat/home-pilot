package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备指令响应 DTO
 * <p>用于指令查询接口的返回结果，展示指令的完整信息和执行状态</p>
 *
 * @author dboat
 */
@Data
@Schema(description = "设备指令响应")
public class CommandRespDTO {

    /** 指令内部主键ID */
    @Schema(description = "指令ID")
    private String id;

    /** 目标设备标识 */
    @Schema(description = "目标设备标识")
    private String deviceId;

    /** 指令唯一ID（UUID），对应 DOWN_CMD header.requestId */
    @Schema(description = "指令唯一ID")
    private String requestId;

    /** 指令编码，如 device_restart、sensor_calibrate、light_switch */
    @Schema(description = "指令编码")
    private String cmdCode;

    /** 指令参数（JSON 字符串） */
    @Schema(description = "指令参数")
    private String params;

    /** 指令超时时间（毫秒） */
    @Schema(description = "超时时间(ms)")
    private Long timeout;

    /**
     * 指令执行状态
     * <p>0=待下发（pending），1=已下发（sent），2=执行成功（success），3=执行失败（failed）</p>
     */
    @Schema(description = "指令状态: 0=待下发, 1=已下发, 2=成功, 3=失败")
    private Integer status;

    /** 指令创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 指令最后更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
