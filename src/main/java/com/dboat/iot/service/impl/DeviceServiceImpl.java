package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.dto.request.*;
import com.dboat.iot.dto.response.DeviceRespDTO;
import com.dboat.iot.entity.Device;
import com.dboat.iot.exception.BusinessException;
import com.dboat.iot.mapper.DeviceMapper;
import com.dboat.iot.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    @Override
    public DeviceRespDTO createDevice(DeviceCreateReqDTO request) {
        // Check if device already exists
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
        device.setStatus(0); // Default offline
        this.save(device);

        return toResponse(device);
    }

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
        if (request.getStatus() != null) {
            device.setStatus(request.getStatus());
        }
        this.updateById(device);

        return toResponse(device);
    }

    @Override
    public void deleteDevice(DeviceDeleteReqDTO request) {
        Device device = this.getById(request.getId());
        if (device == null) {
            throw new BusinessException("Device not found: " + request.getId());
        }
        this.removeById(request.getId()); // Logical delete via @TableLogic
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
        if (request.getStatus() != null) {
            wrapper.eq(Device::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(Device::getUpdateTime);

        Page<Device> page = new Page<>(request.getPageNum(), request.getPageSize());
        IPage<Device> devicePage = this.page(page, wrapper);
        return devicePage.convert(this::toResponse);
    }

    @Override
    public Device autoRegister(String deviceId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceId, deviceId);
        Device device = this.getOne(wrapper);
        if (device == null) {
            device = new Device();
            device.setDeviceId(deviceId);
            device.setDeviceName(deviceId);
            device.setStatus(1);
            this.save(device);
        } else {
            device.setStatus(1);
            this.updateById(device);
        }
        return device;
    }

    @Override
    public void updateStatus(String deviceId, int status) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceId, deviceId);
        Device device = this.getOne(wrapper);
        if (device != null) {
            device.setStatus(status);
            this.updateById(device);
        }
    }

    private DeviceRespDTO toResponse(Device device) {
        DeviceRespDTO response = new DeviceRespDTO();
        response.setId(device.getId());
        response.setDeviceId(device.getDeviceId());
        response.setDeviceName(device.getDeviceName());
        response.setDeviceModel(device.getDeviceModel());
        response.setFirmwareVersion(device.getFirmwareVersion());
        response.setLocation(device.getLocation());
        response.setStatus(device.getStatus());
        response.setCreateTime(device.getCreateTime());
        response.setUpdateTime(device.getUpdateTime());
        return response;
    }
}
