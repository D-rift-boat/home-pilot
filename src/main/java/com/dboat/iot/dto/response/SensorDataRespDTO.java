package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 传感器数据响应 DTO
 * <p>
 * 用于传感器数据查询接口的返回结果，包含 ESP32-S3 上报的完整遥测数据。
 * 数据来源为 InfluxDB 时序数据库。
 * </p>
 *
 * @author dboat
 */
@Data
@Schema(description = "传感器数据响应")
public class SensorDataRespDTO {

    /** 设备唯一标识 */
    @Schema(description = "设备标识")
    private String deviceId;

    /** AHT20 温度值（°C） */
    @Schema(description = "AHT20 温度 (°C)")
    private BigDecimal temperatureAht;

    /** BMP280 温度值（°C） */
    @Schema(description = "BMP280 温度 (°C)")
    private BigDecimal temperatureBmp;

    /** 环境湿度（%RH） */
    @Schema(description = "湿度 (%)")
    private BigDecimal humidity;

    /** 大气压强（hPa） */
    @Schema(description = "气压 (hPa)")
    private BigDecimal pressureHpa;

    /** 海拔高度（米） */
    @Schema(description = "海拔 (m)")
    private BigDecimal altitudeM;

    /** 传感器整体状态：1=正常, 2=失败, 3=部分失败 */
    @Schema(description = "传感器整体状态: 1=正常, 2=失败, 3=部分失败")
    private Integer sensorStatus;

    /** AHT20 传感器状态：1=正常, 2=失败, 3=部分失败 */
    @Schema(description = "AHT20 状态: 1=正常, 2=失败, 3=部分失败")
    private Integer aht20Status;

    /** BMP280 传感器状态：1=正常, 2=失败, 3=部分失败 */
    @Schema(description = "BMP280 状态: 1=正常, 2=失败, 3=部分失败")
    private Integer bmp280Status;

    /** 数据上报时间 */
    @Schema(description = "上报时间")
    private Instant reportTime;
}
