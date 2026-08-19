package com.dboat.iot.api;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.dto.request.DashboardStatsReqDTO;
import com.dboat.iot.dto.response.DashboardStatsRespDTO;
import com.dboat.iot.dto.response.OnlineDeviceRespDTO;
import com.dboat.iot.dto.response.Result;
import com.dboat.iot.entity.Device;
import com.dboat.iot.service.DeviceService;
import com.dboat.iot.utils.DeviceStateStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 仪表盘数据 Controller
 * <p>
 * 提供前端实时监控页面所需的接口：
 * <ul>
 *   <li>面板统计：设备在线数 + 当前温湿度/气压/海拔（轮询刷新）</li>
 *   <li>在线设备列表：所有在线设备及其最新传感器数据</li>
 * </ul>
 * 数据来源：在线数来自 Redis，实时传感器数据来自 Redis 快照（MQTT 上报后写入），设备名称来自 MySQL。
 * 历史区间数据查询走 InfluxDB（见 SensorDataController）。
 * </p>
 *
 * @author dboat
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "仪表盘实时监控数据 API")
public class DashboardController {

    /** 设备服务（获取设备信息） */
    private final DeviceService deviceService;

    /** Redis 设备状态存储（获取实时数据 + 在线设备） */
    private final DeviceStateStore deviceStateStore;

    /** 构造器注入依赖 */
    public DashboardController(DeviceService deviceService,
                                DeviceStateStore deviceStateStore) {
        this.deviceService = deviceService;
        this.deviceStateStore = deviceStateStore;
    }

    /**
     * 获取仪表盘面板统计数据
     * <p>
     * 返回设备在线数和指定设备（或默认第一个在线设备）的当前传感器数据。
     * 前端实时监控页面定时轮询此接口（建议 5~10 秒间隔）。
     * </p>
     */
    @PostMapping("/stats")
    @Operation(summary = "面板统计数据", description = "获取设备在线数 + 当前温湿度/气压/海拔")
    public Result<DashboardStatsRespDTO> getStats(@Valid @RequestBody DashboardStatsReqDTO request) {
        DashboardStatsRespDTO stats = new DashboardStatsRespDTO();

        // 1. 从 Redis 获取在线设备数（原子计数器）
        stats.setOnlineCount((int) deviceStateStore.getUserDeviceOnlineCount("admin"));

        // 2. 确定要查询的设备ID
        String deviceId = request.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            // 未指定设备，取第一个在线设备
            Set<String> onlineIds = deviceStateStore.getOnlineDeviceIds("admin");
            if (!onlineIds.isEmpty()) {
                deviceId = onlineIds.iterator().next();
            }
        }

        // 3. 从 Redis 获取设备最新快照数据（MQTT 上报时写入）
        if (deviceId != null) {
            JSONObject latestData = deviceStateStore.getDeviceLatestDataAsJson(deviceId);
            if (latestData != null) {
                stats.setTemperatureAht(toBigDecimal(latestData.get("tempAht")));
                stats.setHumidity(toBigDecimal(latestData.get("humidity")));
                stats.setPressureHpa(toBigDecimal(latestData.get("pressureHpa")));
                stats.setAltitude(toBigDecimal(latestData.get("altitude")));
                Long ts = latestData.getLong("timestamp");
                if (ts != null) {
                    stats.setReportTime(Instant.ofEpochMilli(ts));
                }
            }
        }

        return Result.ok(stats);
    }

    /**
     * 获取所有在线设备及其最新传感器数据
     * <p>
     * 返回所有在线设备的设备名称、状态和最新上报的温湿度/气压/海拔数据。
     * 前端实时监控页面用于展示设备卡片列表。
     * </p>
     */
    @PostMapping("/onlineDevices")
    @Operation(summary = "在线设备列表", description = "获取所有在线设备及其最新传感器数据")
    public Result<List<OnlineDeviceRespDTO>> getOnlineDevices() {
        List<OnlineDeviceRespDTO> result = new ArrayList<>();

        // 1. 从 Redis 获取所有在线设备ID
        Set<String> onlineIds = deviceStateStore.getOnlineDeviceIds("admin");
        if (onlineIds.isEmpty()) {
            return Result.ok(result);
        }

        // 2. 遍历在线设备，获取设备信息和最新传感器数据
        for (String deviceId : onlineIds) {
            OnlineDeviceRespDTO dto = new OnlineDeviceRespDTO();
            dto.setDeviceId(deviceId);
            dto.setStatus(1); // 在线

            // 从 MySQL 获取设备名称
            Device device = deviceService.getDeviceByDeviceIdRaw(deviceId);
            if (device != null) {
                dto.setDeviceName(device.getDeviceName());
            }

            // 从 Redis 获取设备最新快照数据（MQTT 上报时写入）
            JSONObject latestData = deviceStateStore.getDeviceLatestDataAsJson(deviceId);
            if (latestData != null) {
                dto.setTemperatureAht(toBigDecimal(latestData.get("tempAht")));
                dto.setHumidity(toBigDecimal(latestData.get("humidity")));
                dto.setPressureHpa(toBigDecimal(latestData.get("pressureHpa")));
                dto.setAltitude(toBigDecimal(latestData.get("altitude")));
                Long ts = latestData.getLong("timestamp");
                if (ts != null) {
                    dto.setReportTime(Instant.ofEpochMilli(ts));
                }
            }

            result.add(dto);
        }

        return Result.ok(result);
    }

    /**
     * 安全地将 Object 转换为 BigDecimal
     * <p>
     * Redis JSON 中的数值字段反序列化后可能是 Integer、Long、Double 等类型，
     * 统一转为 BigDecimal 保证精度一致。
     * </p>
     *
     * @param value 原始数值对象
     * @return BigDecimal，值为 null 时返回 null
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
