package com.dboat.iot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.iot.common.constants.DeviceLogEnum;
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
    public void logDeviceAbnormal(String deviceId, int abnormalStatus, String abnormalDesc, String logDetail) {
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setDeviceId(deviceId);
        deviceLog.setLogType(DeviceLogEnum.ABNORMAL.getCode());
        deviceLog.setAbnormalStatus(abnormalStatus);
        deviceLog.setAbnormalDesc(abnormalDesc);
        deviceLog.setLogDetail(logDetail);
        deviceLog.setLogTime(LocalDateTime.now());
        asyncSaveLog(deviceLog);
        log.warn("Device abnormal log recorded for device [{}]: status={}, desc={}", deviceId, abnormalStatus, abnormalDesc);
    }

    @Override
    public DeviceLog getLatestDeviceLogByDeviceId(String deviceId) {
        LambdaQueryWrapper<DeviceLog> queryWrapper = new LambdaQueryWrapper<DeviceLog>()
                .eq(DeviceLog::getDeviceId, deviceId)
                .orderByDesc(DeviceLog::getLogTime)
                .last("limit 1");
        return this.getOne(queryWrapper);
    }
}
