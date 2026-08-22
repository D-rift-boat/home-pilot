package com.dboat.iot.service.impl;

import com.dboat.iot.dto.emqx.DeviceConnectDomainEvent;
import com.dboat.iot.dto.ws.WsUploadDataDTO;
import com.dboat.iot.enums.WsTypeEnum;
import com.dboat.iot.enums.webhook.WebHookEventTypeEnum;
import com.dboat.iot.service.DeviceConnectEventService;
import com.dboat.iot.service.UserDeviceRelService;
import com.dboat.iot.service.ws.WsDistributedPushService;
import com.dboat.iot.utils.DeviceStateService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConnectEventServiceImpl implements DeviceConnectEventService {

    private final RedisTemplate<String, Object> redisTemplate;
    //private final RedissonClient redissonClient;
    @Resource
    private final DeviceStateService deviceStateService;
    @Resource
    private UserDeviceRelService userDeviceRelService;
    @Resource
    private WsDistributedPushService wsPushService;

    @Value("${mqtt.webHookSwitch}")
    private String webHookSwitch;

    public static final String DELAY_QUEUE_NAME = "iot:device:offline:delay_queue";
    private static final String IDEM_PREFIX = "iot:idempotent:connect:";

    @Override
    public void handleConnectDomainEvent(DeviceConnectDomainEvent domainEvent) {
        //webhook开关判断  若未启用 则使用固件主题上报上线、lwt下线
        if ("true".equals(webHookSwitch)){
            //幂等判断
            String idempKey = IDEM_PREFIX + domainEvent.getEventId();
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(idempKey, "1", java.time.Duration.ofMinutes(5));
            if(Boolean.FALSE.equals(absent)){
                log.warn("[connect-event]重复事件丢弃,eventId={}",domainEvent.getEventId());
                return;
            }

            if (WebHookEventTypeEnum.CONNECTED.getCode().equals(domainEvent.getEventType())) {
                handleConnected(domainEvent);
            } else if (WebHookEventTypeEnum.DISCONNECTED.getCode().equals(domainEvent.getEventType())) {
                handleDisconnected(domainEvent);
            }
        }
    }

    /**
     * MQTT连接建立
     */
    private void handleConnected(DeviceConnectDomainEvent domainEvent) {
        String deviceId = domainEvent.getDeviceId();
        deviceStateService.iotDeviceOnline(deviceId);

        // 构建 WS 事件并推送给订阅用户
        Set<String> subscriberUserIds = userDeviceRelService.getSubscriberUserIds(deviceId);
        for (String userId : subscriberUserIds) {
            long onlineCount = deviceStateService.getUserIotDeviceOnlineCount(userId);
            WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
            wsUploadDataDTO.setType(WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode());
            WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
            WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
            deviceDTO.setDeviceId(deviceId);
            dataDTO.setIotDeviceOnlineCount(String.valueOf(onlineCount));
            wsUploadDataDTO.setData(dataDTO);
            wsUploadDataDTO.setDevice(deviceDTO);
            wsPushService.pushToUser(userId, WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode(), wsUploadDataDTO);
        }
    }

    /**
     * MQTT断开连接
     */
    private void handleDisconnected(DeviceConnectDomainEvent domainEvent) {
        String deviceId = domainEvent.getDeviceId();
        deviceStateService.iotDeviceOffline(deviceId);

        // 构建 WS 事件并推送给订阅用户
        Set<String> subscriberUserIds = userDeviceRelService.getSubscriberUserIds(deviceId);
        for (String userId : subscriberUserIds) {
            long remainingCount = deviceStateService.getUserIotDeviceOnlineCount(userId);
            WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
            wsUploadDataDTO.setType(WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode());
            WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
            WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
            deviceDTO.setDeviceId(deviceId);
            dataDTO.setIotDeviceOnlineCount(String.valueOf(remainingCount));
            wsUploadDataDTO.setData(dataDTO);
            wsUploadDataDTO.setDevice(deviceDTO);
            wsPushService.pushToUser(userId, WsTypeEnum.IOT_DEVICE_ONLINE_COUNT.getCode(), wsUploadDataDTO);
        }
    }


    //private void handleDisconnected(DeviceConnectDomainEvent domainEvent) {
    //    String deviceId = domainEvent.getDeviceId();
    //    //1. Redis标记状态 PENDING_OFFLINE
    //    //2. 提交Redisson延迟队列，延迟30s执行下线裁决
    //    RBlockingQueue<DeviceOfflineDelayDTO> blockingQueue = redissonClient.getBlockingQueue(DELAY_QUEUE_NAME);
    //    RDelayedQueue<DeviceOfflineDelayDTO> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    //
    //    DeviceOfflineDelayDTO delayDto = new DeviceOfflineDelayDTO();
    //    delayDto.setDeviceId(deviceId);
    //    delayDto.setTriggerEventId(domainEvent.getEventId());
    //    //延迟30秒
    //    delayedQueue.offer(delayDto,30,java.util.concurrent.TimeUnit.SECONDS);
    //}
}