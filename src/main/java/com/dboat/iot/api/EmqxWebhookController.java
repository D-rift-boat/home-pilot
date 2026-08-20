package com.dboat.iot.api;

import com.dboat.iot.dto.emqx.DeviceConnectDomainEvent;
import com.dboat.iot.dto.emqx.EmqxOfflineExternalDTO;
import com.dboat.iot.dto.emqx.EmqxOnlineExternalDTO;
import com.dboat.iot.enums.webhook.WebHookEventTypeEnum;
import com.dboat.iot.service.DeviceConnectEventService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emqx")
public class EmqxWebhookController {

    // webhook密钥，和EMQX配置的query参数token保持一致
    private final String WEBHOOK_SECRET_TOKEN = "emqx_xxxx_123456";

    @Resource
    private DeviceConnectEventService deviceConnectEventService;



    /**
     * 设备上线回调接口
     */
    @PostMapping("/online")
    public ResponseEntity<Void> onlineCallback(
            @RequestParam(value = "token", required = false) String token,
            @RequestBody EmqxOnlineExternalDTO dto
    ) {
        //鉴权
        //if (!WEBHOOK_TOKEN.equals(token)) {
        //    return ResponseEntity.status(403).build();
        //}
        if (dto.getClientid() == null || dto.getClientid().isBlank()) {
            return ResponseEntity.ok().build();
        }
        //异步提交，立刻返回，不阻塞http回调
        //iotEventTaskExecutor.execute(() -> {
        //});
        DeviceConnectDomainEvent domainEvent = buildOnlineDomainEvent(dto);
        deviceConnectEventService.handleConnectDomainEvent(domainEvent);
        return ResponseEntity.ok().build();
    }

    /**
     * 设备下线回调接口
     */
    @PostMapping("/offline")
    public ResponseEntity<Void> offlineCallback(
            @RequestParam(value = "token", required = false) String token,
            @RequestBody EmqxOfflineExternalDTO dto) {
        //if (!WEBHOOK_TOKEN.equals(token)) {
        //    return ResponseEntity.status(403).build();
        //}
        // 过滤后端服务自身的mqtt客户端，不是硬件IoT设备，直接丢弃
        if("home-pilot-server".equals(dto.getClientid())){
            return ResponseEntity.ok().build();
        }
        if (dto.getClientid() == null || dto.getClientid().isBlank()) {
            return ResponseEntity.ok().build();
        }

        DeviceConnectDomainEvent domainEvent = buildOfflineDomainEvent(dto);
        deviceConnectEventService.handleConnectDomainEvent(domainEvent);
        return ResponseEntity.ok().build();
    }

    /**
     * 构建设备上线事件
     */
    private DeviceConnectDomainEvent buildOnlineDomainEvent(EmqxOnlineExternalDTO dto) {
        DeviceConnectDomainEvent event = new DeviceConnectDomainEvent();
        event.setEventType(WebHookEventTypeEnum.CONNECTED.getCode());
        event.setDeviceId(dto.getClientid());
        event.setMqttUsername(dto.getUsername());
        event.setClientIp(dto.getIpAddress());
        long ts = System.currentTimeMillis();
        event.setEventTs(ts);
        event.setEventId(dto.getClientid() + "_" + ts);
        return event;
    }

    /**
     * 构建设备下线事件
     */
    private DeviceConnectDomainEvent buildOfflineDomainEvent(EmqxOfflineExternalDTO dto) {
        DeviceConnectDomainEvent event = new DeviceConnectDomainEvent();
        event.setEventType(WebHookEventTypeEnum.DISCONNECTED.getCode());
        event.setDeviceId(dto.getClientid());
        event.setMqttUsername(dto.getUsername());
        event.setDisconnectReason(dto.getReason());
        event.setClientIp(dto.getIpAddress());
        event.setEventTs(dto.getDisconnectedAt());
        event.setEventId(dto.getClientid() + "_" + dto.getDisconnectedAt());
        return event;
    }
}