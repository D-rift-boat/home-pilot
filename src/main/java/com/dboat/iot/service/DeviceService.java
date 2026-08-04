package com.dboat.iot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.dto.request.DeviceCreateReqDTO;
import com.dboat.iot.dto.request.DeviceUpdateReqDTO;
import com.dboat.iot.dto.response.DeviceRespDTO;
import com.dboat.iot.entity.Device;

public interface DeviceService extends IService<Device> {

    DeviceRespDTO createDevice(DeviceCreateReqDTO request);

    DeviceRespDTO updateDevice(String id, DeviceUpdateReqDTO request);

    void deleteDevice(String id);

    DeviceRespDTO getDeviceById(String id);

    DeviceRespDTO getDeviceByDeviceId(String deviceId);

    IPage<DeviceRespDTO> listDevices(String deviceId, String deviceName, String deviceModel,
                                       Integer status, int pageNum, int pageSize);

    /**
     * Auto-register device from MQTT message if not exists
     */
    Device autoRegister(String deviceId);

    /**
     * Update device online status
     */
    void updateStatus(String deviceId, int status);
}
