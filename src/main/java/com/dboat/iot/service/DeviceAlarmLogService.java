package com.dboat.iot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.entity.DeviceAlarmLog;

/**
 * 设备告警日志业务服务接口
 * <p>
 * 定义告警记录的异步写入和各类告警触发方法。
 * 告警由流式告警引擎在 MQTT 消息处理过程中调用触发。
 * </p>
 *
 * @author dboat
 */
public interface DeviceAlarmLogService extends IService<DeviceAlarmLog> {

    /**
     * 异步写入告警记录到 MySQL（使用 @Async 异步执行，不阻塞主流程）
     *
     * @param alarmLog 告警日志实体
     */
    void asyncSaveAlarm(DeviceAlarmLog alarmLog);

    /**
     * 触发传感器故障告警
     * <p>当传感器状态码为 2（失败）或 3（部分失败）时调用</p>
     *
     * @param deviceId    设备唯一标识
     * @param alarmDetail 告警详情 JSON（含各传感器状态码和原始数据）
     */
    void triggerSensorFaultAlarm(String deviceId, String alarmDetail);

    /**
     * 触发设备离线告警
     * <p>当收到 EMQX 断连事件时调用</p>
     *
     * @param deviceId     设备唯一标识
     * @param alarmContext 异常上下文（离线前最后传感器状态 JSON）
     */
    void triggerDeviceOfflineAlarm(String deviceId, String alarmContext);
}
