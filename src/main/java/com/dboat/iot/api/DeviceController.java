package com.dboat.iot.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dboat.iot.dto.request.*;
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

    @PostMapping("/create")
    @Operation(summary = "Register device", description = "Manually register a new device")
    public Result<DeviceRespDTO> createDevice(@Valid @RequestBody DeviceCreateReqDTO request) {
        return Result.ok(deviceService.createDevice(request));
    }

    @PostMapping("/update")
    @Operation(summary = "Update device", description = "Update device info by ID")
    public Result<DeviceRespDTO> updateDevice(@Valid @RequestBody DeviceUpdateReqDTO request) {
        return Result.ok(deviceService.updateDevice(request));
    }

    @PostMapping("/delete")
    @Operation(summary = "Delete device", description = "Logically delete a device")
    public Result<Void> deleteDevice(@Valid @RequestBody DeviceDeleteReqDTO request) {
        deviceService.deleteDevice(request);
        return Result.ok();
    }

    @PostMapping("/getById")
    @Operation(summary = "Get device by ID", description = "Get device detail by internal ID")
    public Result<DeviceRespDTO> getDeviceById(@Valid @RequestBody DeviceGetByIdReqDTO request) {
        return Result.ok(deviceService.getDeviceById(request));
    }

    @PostMapping("/getByDeviceId")
    @Operation(summary = "Get device by device ID", description = "Get device detail by unique device identifier")
    public Result<DeviceRespDTO> getDeviceByDeviceId(@Valid @RequestBody DeviceGetByDeviceIdReqDTO request) {
        return Result.ok(deviceService.getDeviceByDeviceId(request));
    }

    @PostMapping("/list")
    @Operation(summary = "List devices", description = "Paginated device list with filters")
    public Result<IPage<DeviceRespDTO>> listDevices(@Valid @RequestBody DeviceListReqDTO request) {
        return Result.ok(deviceService.listDevices(request));
    }
}
