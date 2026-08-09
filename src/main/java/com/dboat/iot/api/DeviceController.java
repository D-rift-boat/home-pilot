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

/**
 * 设备管理 Controller
 * <p>
 * 提供设备资产的 CRUD 和分页查询 REST API。
 * 所有接口统一使用 POST 请求 + DTO 参数封装。
 * 设备在线状态从 Redis 实时获取，非 MySQL。
 * </p>
 *
 * @author dboat
 */
@RestController
@RequestMapping("/api/device")
@Tag(name = "Device Management", description = "设备资产 CRUD 与分页查询 API")
public class DeviceController {

    /** 设备业务服务 */
    private final DeviceService deviceService;

    /** 构造器注入设备服务 */
    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * 手动注册新设备
     */
    @PostMapping("/create")
    @Operation(summary = "注册设备", description = "手动创建新设备记录")
    public Result<DeviceRespDTO> createDevice(@Valid @RequestBody DeviceCreateReqDTO request) {
        return Result.ok(deviceService.createDevice(request));
    }

    /**
     * 更新设备静态属性
     */
    @PostMapping("/update")
    @Operation(summary = "更新设备", description = "根据内部ID更新设备属性")
    public Result<DeviceRespDTO> updateDevice(@Valid @RequestBody DeviceUpdateReqDTO request) {
        return Result.ok(deviceService.updateDevice(request));
    }

    /**
     * 逻辑删除设备（同时清理 Redis 状态）
     */
    @PostMapping("/delete")
    @Operation(summary = "删除设备", description = "逻辑删除设备记录")
    public Result<Void> deleteDevice(@Valid @RequestBody DeviceDeleteReqDTO request) {
        deviceService.deleteDevice(request);
        return Result.ok();
    }

    /**
     * 根据内部主键ID查询设备详情
     */
    @PostMapping("/getById")
    @Operation(summary = "根据ID查询", description = "根据内部主键ID获取设备详情")
    public Result<DeviceRespDTO> getDeviceById(@Valid @RequestBody DeviceGetByIdReqDTO request) {
        return Result.ok(deviceService.getDeviceById(request));
    }

    /**
     * 根据设备业务标识查询设备详情
     */
    @PostMapping("/getByDeviceId")
    @Operation(summary = "根据设备标识查询", description = "根据 device_id 获取设备详情")
    public Result<DeviceRespDTO> getDeviceByDeviceId(@Valid @RequestBody DeviceGetByDeviceIdReqDTO request) {
        return Result.ok(deviceService.getDeviceByDeviceId(request));
    }

    /**
     * 分页查询设备列表（支持按设备ID/名称/型号过滤）
     */
    @PostMapping("/list")
    @Operation(summary = "分页列表", description = "分页查询设备列表，支持条件过滤")
    public Result<IPage<DeviceRespDTO>> listDevices(@Valid @RequestBody DeviceListReqDTO request) {
        return Result.ok(deviceService.listDevices(request));
    }
}
