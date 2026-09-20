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
     * 记录设备异常日志
     *
     * @param deviceId       设备唯一标识
     * @param abnormalStatus 异常状态码
     * @param abnormalDesc   异常描述
     * @param logDetail      异常详情 JSON
     */
    void logDeviceAbnormal(String deviceId, int abnormalStatus, String abnormalDesc, String logDetail);

    /**
     * 根据设备ID获取最新日志
     *
     * @param deviceId 设备唯一标识
     * @return 设备日志
     */
    DeviceLog getLatestDeviceLogByDeviceId(String deviceId);
}
