package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 在线设备信息响应 DTO
 * <p>
 * 用于实时监控页面展示所有在线设备及其最新传感器数据。
 * 设备基本信息来自 MySQL，在线状态来自 Redis，传感器数据来自 InfluxDB。
 * </p>
 *
 * @author dboat
 */
@Data
@Schema(description = "在线设备信息响应")
public class OnlineDeviceRespDTO {

    /** 设备唯一标识 */
    @Schema(description = "设备标识")
    private String deviceId;

    /** 设备名称 */
    @Schema(description = "设备名称")
    private String deviceName;

    /** 设备在线状态：0=离线, 1=在线, 2=异常 */
    @Schema(description = "在线状态: 0=离线, 1=在线, 2=异常")
    private Integer status;

    /** 当前温度 AHT20（°C） */
    @Schema(description = "当前温度 (°C)")
    private BigDecimal temperatureAht;

    /** 当前湿度（%RH） */
    @Schema(description = "当前湿度 (%)")
    private BigDecimal humidity;

    /** 当前大气压强（hPa） */
    @Schema(description = "当前气压 (hPa)")
    private BigDecimal pressureHpa;

    /** 当前海拔高度（米） */
    @Schema(description = "当前海拔 (m)")
    private BigDecimal altitude;

    /** 数据上报时间 */
    @Schema(description = "最新数据上报时间")
    private Instant reportTime;
}
