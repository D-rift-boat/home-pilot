package com.dboat.iot.api;

import com.dboat.iot.dto.request.CommandSendReqDTO;
import com.dboat.iot.dto.response.CommandRespDTO;
import com.dboat.iot.dto.response.Result;
import com.dboat.iot.service.DeviceCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/command")
@Tag(name = "Device Command", description = "Command send and query APIs")
public class DeviceCommandController {

    private final DeviceCommandService deviceCommandService;

    public DeviceCommandController(DeviceCommandService deviceCommandService) {
        this.deviceCommandService = deviceCommandService;
    }

    @PostMapping("/send")
    @Operation(summary = "Send command", description = "Send a control command to a device via MQTT")
    public Result<CommandRespDTO> sendCommand(@Valid @RequestBody CommandSendReqDTO request) {
        return Result.ok(deviceCommandService.sendCommand(request));
    }

    @GetMapping("/device/{deviceId}")
    @Operation(summary = "Get commands by device", description = "Get all commands for a device")
    public Result<List<CommandRespDTO>> getCommandsByDeviceId(@PathVariable String deviceId) {
        return Result.ok(deviceCommandService.getCommandsByDeviceId(deviceId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get command by ID", description = "Get command detail by ID")
    public Result<CommandRespDTO> getCommandById(@PathVariable String id) {
        return Result.ok(deviceCommandService.getCommandById(id));
    }
}
