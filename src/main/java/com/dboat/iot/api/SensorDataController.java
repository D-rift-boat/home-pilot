package com.dboat.iot.api;

import com.dboat.iot.dto.request.SensorDataLatestReqDTO;
import com.dboat.iot.dto.request.SensorDataQueryReqDTO;
import com.dboat.iot.dto.response.Result;
import com.dboat.iot.dto.response.SensorDataRespDTO;
import com.dboat.iot.service.TelemetryDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 传感器数据查询 Controller
 * <p>
 * 提供传感器遥测数据的历史查询和最新值查询 REST API。
 * 数据来源于 InfluxDB 时序数据库。
 * </p>
 *
 * @author dboat
 */
@RestController
@RequestMapping("/api/sensorData")
@Tag(name = "Sensor Data", description = "传感器数据查询 API")
public class SensorDataController {

    /** 传感器数据业务服务 */
    private final TelemetryDataService telemetryDataService;

    /** 构造器注入传感器数据服务 */
    public SensorDataController(TelemetryDataService telemetryDataService) {
        this.telemetryDataService = telemetryDataService;
    }

    /**
     * 按时间范围查询传感器历史数据
     */
    @PostMapping("/history")
    @Operation(summary = "历史数据查询", description = "按设备ID和时间范围查询传感器遥测数据")
    public Result<List<SensorDataRespDTO>> queryHistory(@Valid @RequestBody SensorDataQueryReqDTO request) {
        return Result.ok(telemetryDataService.querySensorData(request));
    }

    /**
     * 获取设备最新一条传感器上报数据
     */
    @PostMapping("/latest")
    @Operation(summary = "最新数据查询", description = "获取指定设备的最新传感器数据")
    public Result<SensorDataRespDTO> getLatest(@Valid @RequestBody SensorDataLatestReqDTO request) {
        SensorDataRespDTO data = telemetryDataService.getLatestSensorData(request);
        return Result.ok(data);
    }
}
