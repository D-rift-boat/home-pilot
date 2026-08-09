package com.dboat.iot.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.entity.DeviceAlarmLog;
import com.dboat.iot.enums.AlarmTypeEnum;
import com.dboat.iot.mapper.DeviceAlarmLogMapper;
import com.dboat.iot.service.DeviceAlarmLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 设备告警日志业务服务实现类
 * <p>
 * 实现告警记录的异步写入和各类告警触发逻辑。
 * 告警保存使用 @Async 异步执行，避免阻塞 MQTT 消息处理主流程。
 * </p>
 *
 * @author dboat
 */
@Service
public class DeviceAlarmLogServiceImpl extends ServiceImpl<DeviceAlarmLogMapper, DeviceAlarmLog>
        implements DeviceAlarmLogService {

    private static final Logger log = LoggerFactory.getLogger(DeviceAlarmLogServiceImpl.class);

    /**
     * 告警级别：提示
     */
    private static final int ALARM_LEVEL_INFO = 1;
    /**
     * 告警级别：警告
     */
    private static final int ALARM_LEVEL_WARNING = 2;
    /**
     * 告警级别：严重
     */
    private static final int ALARM_LEVEL_CRITICAL = 3;

    @Async
    @Override
    public void asyncSaveAlarm(DeviceAlarmLog alarmLog) {
        try {
            this.save(alarmLog);
            log.info("Alarm saved: device={}, type={}, level={}",
                    alarmLog.getDeviceId(), alarmLog.getAlarmType(), alarmLog.getAlarmLevel());
        } catch (Exception e) {
            log.error("Failed to save alarm log for device [{}]: {}", alarmLog.getDeviceId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerSensorFaultAlarm(String deviceId, String alarmDetail) {
        DeviceAlarmLog alarm = new DeviceAlarmLog();
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(AlarmTypeEnum.SENSOR_FAULT.name());
        alarm.setAlarmLevel(ALARM_LEVEL_WARNING);
        alarm.setAlarmDetail(alarmDetail);
        alarm.setHandleStatus(0);
        alarm.setTriggerTime(LocalDateTime.now());

        asyncSaveAlarm(alarm);
        log.warn("Sensor fault alarm triggered for device: {}", deviceId);
    }

    @Override
    public void triggerDeviceOfflineAlarm(String deviceId, String alarmContext) {
        DeviceAlarmLog alarm = new DeviceAlarmLog();
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(AlarmTypeEnum.DEVICE_OFFLINE.name());
        alarm.setAlarmLevel(ALARM_LEVEL_CRITICAL);
        alarm.setAlarmContext(alarmContext);
        alarm.setHandleStatus(0);
        alarm.setTriggerTime(LocalDateTime.now());

        // 构建告警详情 JSON
        JSONObject detail = new JSONObject();
        detail.put("event", "device_disconnected");
        detail.put("triggerSource", "EMQX_SYSTEM_EVENT");
        alarm.setAlarmDetail(detail.toJSONString());

        asyncSaveAlarm(alarm);
        log.warn("Device offline alarm triggered for device: {}", deviceId);
    }
}
