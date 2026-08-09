package com.dboat.iot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.iot.entity.DeviceLog;

/**
 * 设备日志业务服务接口
 * <p>
 * 定义设备日志的写入方法，包括上线、离线、异常等事件记录。
 * 日志由 MQTT 消息处理过程中异步触发写入。
 * </p>
 *
 * @author dboat
 */
public interface DeviceLogService extends IService<DeviceLog> {

    /**
     * 异步写入设备日志到 MySQL（使用 @Async 异步执行，不阻塞主流程）
     *
     * @param deviceLog 设备日志实体
     */
    void asyncSaveLog(DeviceLog deviceLog);

    /**
     * 记录设备上线日志
     *
     * @param deviceId  设备唯一标识
     * @param logDetail 上线详情 JSON（如传感器状态信息）
     */
    void logDeviceOnline(String deviceId, String logDetail);

    /**
     * 记录设备离线日志
     *
     * @param deviceId  设备唯一标识
     * @param logDetail 离线详情 JSON（如最后传感器状态）
     */
    void logDeviceOffline(String deviceId, String logDetail);

    /**
     * 记录设备异常日志
     *
     * @param deviceId       设备唯一标识
     * @param abnormalStatus 异常状态码
     * @param abnormalDesc   异常描述
     * @param logDetail      异常详情 JSON
     */
    void logDeviceAbnormal(String deviceId, int abnormalStatus, String abnormalDesc, String logDetail);
}
