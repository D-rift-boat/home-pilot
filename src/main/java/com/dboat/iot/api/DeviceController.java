package com.dboat.iot.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dboat.iot.dto.request.DeviceCreateReqDTO;
import com.dboat.iot.dto.request.DeviceUpdateReqDTO;
import com.dboat.iot.dto.response.DeviceRespDTO;
import com.dboat.iot.dto.response.Result;
import com.dboat.iot.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device")
@Tag(name = "Device Management", description = "Device CRUD and query APIs")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @Operation(summary = "Register device", description = "Manually register a new device")
    public Result<DeviceRespDTO> createDevice(@Valid @RequestBody DeviceCreateReqDTO request) {
        return Result.ok(deviceService.createDevice(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update device", description = "Update device info by ID")
    public Result<DeviceRespDTO> updateDevice(@PathVariable String id,
                                                @RequestBody DeviceUpdateReqDTO request) {
        return Result.ok(deviceService.updateDevice(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete device", description = "Logically delete a device")
    public Result<Void> deleteDevice(@PathVariable String id) {
        deviceService.deleteDevice(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get device by ID", description = "Get device detail by internal ID")
    public Result<DeviceRespDTO> getDeviceById(@PathVariable String id) {
        return Result.ok(deviceService.getDeviceById(id));
    }

    @GetMapping("/by-device-id/{deviceId}")
    @Operation(summary = "Get device by device ID", description = "Get device detail by unique device identifier")
    public Result<DeviceRespDTO> getDeviceByDeviceId(@PathVariable String deviceId) {
        return Result.ok(deviceService.getDeviceByDeviceId(deviceId));
    }

    @GetMapping("/list")
    @Operation(summary = "List devices", description = "Paginated device list with filters")
    public Result<IPage<DeviceRespDTO>> listDevices(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) String deviceModel,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(deviceService.listDevices(deviceId, deviceName, deviceModel, status, pageNum, pageSize));
    }
}
