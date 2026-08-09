package com.dboat.iot.service;

import com.dboat.iot.dto.request.SensorDataLatestReqDTO;
import com.dboat.iot.dto.request.SensorDataQueryReqDTO;
import com.dboat.iot.dto.response.SensorDataRespDTO;
import com.dboat.iot.entity.SensorData;

import java.util.List;

/**
 * 传感器数据业务服务接口
 * <p>
 * 定义传感器遥测数据的保存、历史查询、最新值查询等操作。
 * 数据存储在 InfluxDB 时序数据库中。
 * </p>
 *
 * @author dboat
 */
public interface SensorDataService {

    /**
     * 保存传感器遥测数据到 InfluxDB
     *
     * @param sensorData 传感器数据实体
     */
    void saveSensorData(SensorData sensorData);

    /**
     * 按时间范围查询传感器历史数据
     *
     * @param request 查询请求 DTO（含设备ID、起止时间）
     * @return 传感器数据列表
     */
    List<SensorDataRespDTO> querySensorData(SensorDataQueryReqDTO request);

    /**
     * 获取设备最新一条传感器数据（对外接口层调用）
     *
     * @param request 查询请求 DTO
     * @return 最新传感器数据，无数据时返回 null
     */
    SensorDataRespDTO getLatestSensorData(SensorDataLatestReqDTO request);

    /**
     * 获取设备最新一条原始传感器数据（供内部模块调用，如告警上下文构建）
     *
     * @param deviceId 设备唯一标识
     * @return 原始传感器数据实体，无数据时返回 null
     */
    SensorData getLatestSensorDataRaw(String deviceId);
}
