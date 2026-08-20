package com.dboat.iot.service;

import com.dboat.iot.dto.emqx.DeviceConnectDomainEvent;

public interface DeviceConnectEventService {
    /**
     * 处理设备连接域事件
     * @param domainEvent 设备连接域事件
     */
    void handleConnectDomainEvent(DeviceConnectDomainEvent domainEvent);
}