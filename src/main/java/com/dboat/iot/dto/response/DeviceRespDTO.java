package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备信息响应 DTO
 * <p>
 * 用于设备查询接口的返回结果，包含设备静态属性（来自 MySQL）
 * 和实时在线状态（来自 Redis）。
 * </p>
 *
 * @author dboat
 */
@Data
@Schema(description = "设备信息响应")
public class DeviceRespDTO {

    /** 设备内部主键ID */
    @Schema(description = "内部主键ID")
    private String id;

    /** 设备唯一业务标识 */
    @Schema(description = "设备唯一标识")
    private String deviceId;

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

    /**
     * 设备在线状态（从 Redis 实时获取）
     * <p>0=离线, 1=在线, 2=异常</p>
     */
    @Schema(description = "设备在线状态: 0=离线, 1=在线, 2=异常")
    private Integer status;

    /** 记录创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 记录更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
