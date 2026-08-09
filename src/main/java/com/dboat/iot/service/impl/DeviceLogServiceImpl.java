package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.entity.DeviceLog;
import com.dboat.iot.mapper.DeviceLogMapper;
import com.dboat.iot.service.DeviceLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 设备日志业务服务实现类
 * <p>
 * 实现设备日志的异步写入和各类事件记录逻辑。
 * 日志保存使用 @Async 异步执行，避免阻塞 MQTT 消息处理主流程。
 * </p>
 *
 * @author dboat
 */
@Service
public class DeviceLogServiceImpl extends ServiceImpl<DeviceLogMapper, DeviceLog>
        implements DeviceLogService {

    private static final Logger log = LoggerFactory.getLogger(DeviceLogServiceImpl.class);

    /** 日志类型：设备上线 */
    private static final String LOG_TYPE_ONLINE = "ONLINE";
    /** 日志类型：设备离线 */
    private static final String LOG_TYPE_OFFLINE = "OFFLINE";
    /** 日志类型：设备异常 */
    private static final String LOG_TYPE_ABNORMAL = "ABNORMAL";

    @Async
    @Override
    public void asyncSaveLog(DeviceLog deviceLog) {
        try {
            this.save(deviceLog);
            log.info("Device log saved: device={}, type={}", deviceLog.getDeviceId(), deviceLog.getLogType());
        } catch (Exception e) {
            log.error("Failed to save device log for device [{}]: {}", deviceLog.getDeviceId(), e.getMessage(), e);
        }
    }

    @Override
    public void logDeviceOnline(String deviceId, String logDetail) {
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setDeviceId(deviceId);
        deviceLog.setLogType(LOG_TYPE_ONLINE);
        deviceLog.setLogDetail(logDetail);
        deviceLog.setLogTime(LocalDateTime.now());
        asyncSaveLog(deviceLog);
        log.info("Device online log recorded for device: {}", deviceId);
    }

    @Override
    public void logDeviceOffline(String deviceId, String logDetail) {
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setDeviceId(deviceId);
        deviceLog.setLogType(LOG_TYPE_OFFLINE);
        deviceLog.setLogDetail(logDetail);
        deviceLog.setLogTime(LocalDateTime.now());
        asyncSaveLog(deviceLog);
        log.warn("Device offline log recorded for device: {}", deviceId);
    }

    @Override
    public void logDeviceAbnormal(String deviceId, int abnormalStatus, String abnormalDesc, String logDetail) {
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setDeviceId(deviceId);
        deviceLog.setLogType(LOG_TYPE_ABNORMAL);
        deviceLog.setAbnormalStatus(abnormalStatus);
        deviceLog.setAbnormalDesc(abnormalDesc);
        deviceLog.setLogDetail(logDetail);
        deviceLog.setLogTime(LocalDateTime.now());
        asyncSaveLog(deviceLog);
        log.warn("Device abnormal log recorded for device [{}]: status={}, desc={}", deviceId, abnormalStatus, abnormalDesc);
    }
}
