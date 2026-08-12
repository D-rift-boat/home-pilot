package com.dboat.iot.api;

import com.dboat.iot.dto.request.DashboardStatsReqDTO;
import com.dboat.iot.dto.response.DashboardStatsRespDTO;
import com.dboat.iot.dto.response.OnlineDeviceRespDTO;
import com.dboat.iot.dto.response.Result;
import com.dboat.iot.entity.Device;
import com.dboat.iot.entity.SensorData;
import com.dboat.iot.service.DeviceService;
import com.dboat.iot.service.SensorDataService;
import com.dboat.iot.utils.DeviceStateStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
 * 数据来源：在线数来自 Redis，传感器数据来自 InfluxDB，设备名称来自 MySQL。
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
    /** 传感器数据服务（获取最新数据） */
    private final SensorDataService sensorDataService;
    /** Redis 设备状态存储（获取在线设备） */
    private final DeviceStateStore deviceStateStore;

    /** 构造器注入依赖 */
    public DashboardController(DeviceService deviceService,
                                SensorDataService sensorDataService,
                                DeviceStateStore deviceStateStore) {
        this.deviceService = deviceService;
        this.sensorDataService = sensorDataService;
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

        // 1. 从 Redis 获取在线设备数
        stats.setOnlineCount(deviceStateStore.getOnlineCount());

        // 2. 确定要查询的设备ID
        String deviceId = request.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            // 未指定设备，取第一个在线设备
            Set<String> onlineIds = deviceStateStore.getOnlineDeviceIds();
            if (!onlineIds.isEmpty()) {
                deviceId = onlineIds.iterator().next();
            }
        }

        // 3. 从 InfluxDB 获取最新传感器数据
        if (deviceId != null) {
            SensorData latestData = sensorDataService.getLatestSensorDataRaw(deviceId);
            if (latestData != null) {
                stats.setTemperatureAht(latestData.getTemperatureAht());
                stats.setHumidity(latestData.getHumidity());
                stats.setPressureHpa(latestData.getPressureHpa());
                stats.setAltitude(latestData.getAltitudeM());
                stats.setReportTime(latestData.getReportTime());
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
        Set<String> onlineIds = deviceStateStore.getOnlineDeviceIds();
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

            // 从 InfluxDB 获取最新传感器数据
            SensorData latestData = sensorDataService.getLatestSensorDataRaw(deviceId);
            if (latestData != null) {
                dto.setTemperatureAht(latestData.getTemperatureAht());
                dto.setHumidity(latestData.getHumidity());
                dto.setPressureHpa(latestData.getPressureHpa());
                dto.setAltitude(latestData.getAltitudeM());
                dto.setReportTime(latestData.getReportTime());
            }

            result.add(dto);
        }

        return Result.ok(result);
    }
}
