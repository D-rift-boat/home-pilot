package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.dto.request.*;
import com.dboat.iot.dto.response.DeviceRespDTO;
import com.dboat.iot.entity.Device;
import com.dboat.iot.enums.DeviceOnlineStatusEnum;
import com.dboat.iot.exception.BusinessException;
import com.dboat.iot.mapper.DeviceMapper;
import com.dboat.iot.service.DeviceService;
import com.dboat.iot.utils.DeviceStateStore;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 设备资产业务服务实现类
 * <p>
 * 实现设备 CRUD、自动注册、状态管理等业务逻辑。
 * 设备在线状态统一由 Redis 维护（通过 {@link com.dboat.iot.utils.DeviceStateStore}），
 * 查询接口返回时从 Redis 实时获取在线状态填充到响应 DTO 中。
 * </p>
 *
 * @author dboat
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    /** Redis 设备状态存储工具 */
    @Resource
    private DeviceStateStore deviceStateStore;

    /**
     * 手动创建设备
     * <p>先检查 device_id 是否已存在，存在则抛出业务异常</p>
     */
    @Override
    public DeviceRespDTO createDevice(DeviceCreateReqDTO request) {
        // 检查设备是否已存在
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceId, request.getDeviceId());
        if (this.count(wrapper) > 0) {
            throw new BusinessException("Device already exists: " + request.getDeviceId());
        }

        Device device = new Device();
        device.setDeviceId(request.getDeviceId());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceModel(request.getDeviceModel());
        device.setFirmwareVersion(request.getFirmwareVersion());
        device.setLocation(request.getLocation());
        this.save(device);

        return toResponse(device);
    }

    /**
     * 更新设备静态属性（仅更新非空字段）
     */
    @Override
    public DeviceRespDTO updateDevice(DeviceUpdateReqDTO request) {
        Device device = this.getById(request.getId());
        if (device == null) {
            throw new BusinessException("Device not found: " + request.getId());
        }

        if (StringUtils.hasText(request.getDeviceName())) {
            device.setDeviceName(request.getDeviceName());
        }
        if (StringUtils.hasText(request.getDeviceModel())) {
            device.setDeviceModel(request.getDeviceModel());
        }
        if (StringUtils.hasText(request.getFirmwareVersion())) {
            device.setFirmwareVersion(request.getFirmwareVersion());
        }
        if (StringUtils.hasText(request.getLocation())) {
            device.setLocation(request.getLocation());
        }
        if (StringUtils.hasText(request.getProductId())) {
            device.setProductId(request.getProductId());
        }
        this.updateById(device);

        return toResponse(device);
    }

    /**
     * 逻辑删除设备，同时清理 Redis 中的设备状态缓存
     */
    @Override
    public void deleteDevice(DeviceDeleteReqDTO request) {
        Device device = this.getById(request.getId());
        if (device == null) {
            throw new BusinessException("Device not found: " + request.getId());
        }
        this.removeById(request.getId()); // Logical delete via @TableLogic
        // 同时清理 Redis 状态
        deviceStateStore.removeDeviceState(device.getDeviceId());
    }

    @Override
    public DeviceRespDTO getDeviceById(DeviceGetByIdReqDTO request) {
        Device device = this.getById(request.getId());
        if (device == null) {
            throw new BusinessException("Device not found: " + request.getId());
        }
        return toResponse(device);
    }

    @Override
    public DeviceRespDTO getDeviceByDeviceId(DeviceGetByDeviceIdReqDTO request) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceId, request.getDeviceId());
        Device device = this.getOne(wrapper);
        if (device == null) {
            throw new BusinessException("Device not found: " + request.getDeviceId());
        }
        return toResponse(device);
    }

    @Override
    public IPage<DeviceRespDTO> listDevices(DeviceListReqDTO request) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getDeviceId())) {
            wrapper.like(Device::getDeviceId, request.getDeviceId());
        }
        if (StringUtils.hasText(request.getDeviceName())) {
            wrapper.like(Device::getDeviceName, request.getDeviceName());
        }
        if (StringUtils.hasText(request.getDeviceModel())) {
            wrapper.eq(Device::getDeviceModel, request.getDeviceModel());
        }
        wrapper.orderByDesc(Device::getUpdateTime);

        Page<Device> page = new Page<>(request.getPageNum(), request.getPageSize());
        IPage<Device> devicePage = this.page(page, wrapper);
        return devicePage.convert(this::toResponse);
    }

    /**
     * MQTT 数据上报时自动注册设备（幂等操作）
     * <p>设备不存在则创建，已存在则直接返回</p>
     */
    @Override
    public Device autoRegister(String deviceId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceId, deviceId);
        Device device = this.getOne(wrapper);
        if (device == null) {
            device = new Device();
            device.setDeviceId(deviceId);
            device.setDeviceName(deviceId);
            this.save(device);
        }
        return device;
    }

    @Override
    public void updateStatus(String deviceId, int status) {
        // 设备在线状态统一由 Redis 管理
        if (status == DeviceOnlineStatusEnum.OFFLINE.getCode()) {
            deviceStateStore.userDeviceOffline(deviceId);
        }
        // 在线状态由 MQTT 上报时 MqttMessageHandler 自动写入 Redis，此处无需额外处理
    }

    @Override
    public Device getDeviceByDeviceIdRaw(String deviceId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceId, deviceId);
        return this.getOne(wrapper);
    }

    /**
     * 实体转响应 DTO（内部方法）
     * <p>从 Redis 获取实时在线状态填充到响应中</p>
     */
    private DeviceRespDTO toResponse(Device device) {
        DeviceRespDTO response = new DeviceRespDTO();
        response.setId(device.getId());
        response.setDeviceId(device.getDeviceId());
        response.setDeviceName(device.getDeviceName());
        response.setDeviceModel(device.getDeviceModel());
        response.setFirmwareVersion(device.getFirmwareVersion());
        response.setLocation(device.getLocation());
        response.setCreateTime(device.getCreateTime());
        response.setUpdateTime(device.getUpdateTime());

        // 从 Redis 判断设备是否在线（有 latest key 即为在线）
        String latestData = deviceStateStore.getDeviceLatestData(device.getDeviceId());
        if (latestData != null) {
            response.setStatus(DeviceOnlineStatusEnum.ONLINE.getCode());
        } else {
            response.setStatus(DeviceOnlineStatusEnum.OFFLINE.getCode());
        }
        return response;
    }
}
