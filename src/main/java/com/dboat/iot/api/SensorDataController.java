package com.dboat.iot.api;

import com.dboat.iot.dto.request.SensorDataQueryReqDTO;
import com.dboat.iot.dto.response.Result;
import com.dboat.iot.dto.response.SensorDataRespDTO;
import com.dboat.iot.service.SensorDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensor-data")
@Tag(name = "Sensor Data", description = "Sensor data query APIs")
public class SensorDataController {

    private final SensorDataService sensorDataService;

    public SensorDataController(SensorDataService sensorDataService) {
        this.sensorDataService = sensorDataService;
    }

    @GetMapping("/history")
    @Operation(summary = "Query history data", description = "Query sensor data by device ID and time range")
    public Result<List<SensorDataRespDTO>> queryHistory(@Valid SensorDataQueryReqDTO request) {
        return Result.ok(sensorDataService.querySensorData(request));
    }

    @GetMapping("/latest/{deviceId}")
    @Operation(summary = "Get latest data", description = "Get the latest sensor data for a device")
    public Result<SensorDataRespDTO> getLatest(@PathVariable String deviceId) {
        SensorDataRespDTO data = sensorDataService.getLatestSensorData(deviceId);
        return Result.ok(data);
    }
}
