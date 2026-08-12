package com.dboat.iot.api;

import com.dboat.iot.dto.request.CommandGetByIdReqDTO;
import com.dboat.iot.dto.request.CommandQueryReqDTO;
import com.dboat.iot.dto.request.CommandSendReqDTO;
import com.dboat.iot.dto.response.CommandRespDTO;
import com.dboat.iot.dto.response.Result;
import com.dboat.iot.service.DeviceCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备指令管理 Controller
 * <p>
 * 提供设备控制指令的下发、查询 REST API。
 * 指令通过 MQTT 主题 iot/cmd/{deviceId} 以标准 DOWN_CMD 格式发送到设备端。
 * </p>
 *
 * @author dboat
 */
@RestController
@RequestMapping("/api/command")
@Tag(name = "Device Command", description = "设备指令下发与查询 API")
public class DeviceCommandController {

    /** 设备指令业务服务 */
    private final DeviceCommandService deviceCommandService;

    /** 构造器注入指令服务 */
    public DeviceCommandController(DeviceCommandService deviceCommandService) {
        this.deviceCommandService = deviceCommandService;
    }

    /**
     * 向指定设备下发控制指令（通过 MQTT 发送）
     */
    @PostMapping("/send")
    @Operation(summary = "下发指令", description = "通过 MQTT 向设备发送控制指令")
    public Result<CommandRespDTO> sendCommand(@Valid @RequestBody CommandSendReqDTO request) {
        return Result.ok(deviceCommandService.sendCommand(request));
    }

    /**
     * 查询指定设备的所有指令记录
     */
    @PostMapping("/listByDevice")
    @Operation(summary = "按设备查询", description = "查询指定设备的所有指令记录")
    public Result<List<CommandRespDTO>> getCommandsByDeviceId(@Valid @RequestBody CommandQueryReqDTO request) {
        return Result.ok(deviceCommandService.getCommandsByDeviceId(request));
    }

    /**
     * 根据指令ID查询指令详情
     */
    @PostMapping("/getById")
    @Operation(summary = "根据ID查询", description = "根据指令ID获取指令详情")
    public Result<CommandRespDTO> getCommandById(@Valid @RequestBody CommandGetByIdReqDTO request) {
        return Result.ok(deviceCommandService.getCommandById(request));
    }
}
