package com.dboat.iot.dto.emqx;

import com.dboat.iot.enums.webhook.WebHookEventTypeEnum;
import lombok.Data;

@Data
public class DeviceConnectDomainEvent {
    private String eventType;
    private String deviceId;
    private String mqttUsername;
    private String disconnectReason;
    private Long eventTs;
    private String clientIp;
    //幂等key
    private String eventId;
}

