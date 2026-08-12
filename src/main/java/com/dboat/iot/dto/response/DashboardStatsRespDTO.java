package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 仪表盘面板统计响应 DTO
 * <p>
 * 用于实时监控页面顶部面板数据展示，包含：
 * <ul>
 *   <li>设备在线数（来自 Redis）</li>
 *   <li>当前温度、湿度、气压、海拔（来自 InfluxDB 最新数据）</li>
 * </ul>
 * 前端定时轮询此接口实现实时监控刷新。
 * </p>
 *
 * @author dboat
 */
@Data
@Schema(description = "仪表盘面板统计响应")
public class DashboardStatsRespDTO {

    /** 当前在线设备数量 */
    @Schema(description = "在线设备数")
    private Integer onlineCount;

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
