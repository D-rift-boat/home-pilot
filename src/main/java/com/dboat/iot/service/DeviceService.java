package com.dboat.iot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.dto.request.*;
import com.dboat.iot.dto.response.DeviceRespDTO;
import com.dboat.iot.entity.Device;

public interface DeviceService extends IService<Device> {

    DeviceRespDTO createDevice(DeviceCreateReqDTO request);

    DeviceRespDTO updateDevice(DeviceUpdateReqDTO request);

    void deleteDevice(DeviceDeleteReqDTO request);

    DeviceRespDTO getDeviceById(DeviceGetByIdReqDTO request);

    DeviceRespDTO getDeviceByDeviceId(DeviceGetByDeviceIdReqDTO request);

    IPage<DeviceRespDTO> listDevices(DeviceListReqDTO request);

    /**
     * Auto-register device from MQTT message if not exists
     */
    Device autoRegister(String deviceId);

    /**
     * Update device online status
     */
    void updateStatus(String deviceId, int status);
}
