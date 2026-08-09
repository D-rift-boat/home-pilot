package com.dboat.iot.service.impl;

import com.dboat.iot.config.InfluxDBConfig;
import com.dboat.iot.dto.request.SensorDataLatestReqDTO;
import com.dboat.iot.dto.request.SensorDataQueryReqDTO;
import com.dboat.iot.dto.response.SensorDataRespDTO;
import com.dboat.iot.entity.SensorData;
import com.dboat.iot.service.SensorDataService;
import com.dboat.iot.utils.InfluxDBUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 传感器数据业务服务实现类
 * <p>
 * 实现传感器遥测数据的保存（写入 InfluxDB）、历史查询、最新值查询。
 * 通过 {@link com.dboat.iot.utils.InfluxDBUtils} 操作 InfluxDB 时序数据库。
 * </p>
 *
 * @author dboat
 */
@Service
public class SensorDataServiceImpl implements SensorDataService {

    private static final Logger log = LoggerFactory.getLogger(SensorDataServiceImpl.class);

    /** InfluxDB 操作工具类 */
    private final InfluxDBUtils influxDBUtils;
    /** InfluxDB 配置（获取 bucket、org 等参数） */
    private final InfluxDBConfig influxDBConfig;

    public SensorDataServiceImpl(InfluxDBUtils influxDBUtils, InfluxDBConfig influxDBConfig) {
        this.influxDBUtils = influxDBUtils;
        this.influxDBConfig = influxDBConfig;
    }

    @Override
    public void saveSensorData(SensorData sensorData) {
        if (sensorData.getReportTime() == null) {
            sensorData.setReportTime(Instant.now());
        }
        influxDBUtils.writeSensorData(influxDBConfig.getBucket(), sensorData);
        log.debug("Saved sensor telemetry data for device: {}", sensorData.getDeviceId());
    }

    @Override
    public List<SensorDataRespDTO> querySensorData(SensorDataQueryReqDTO request) {
        Instant start = request.getStartTime() != null
                ? request.getStartTime().toInstant(ZoneOffset.UTC)
                : Instant.now().minusSeconds(3600); // Default: last 1 hour
        Instant end = request.getEndTime() != null
                ? request.getEndTime().toInstant(ZoneOffset.UTC)
                : Instant.now();

        List<SensorData> dataList = influxDBUtils.querySensorData(
                influxDBConfig.getBucket(),
                request.getDeviceId(),
                start,
                end
        );

        return dataList.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public SensorDataRespDTO getLatestSensorData(SensorDataLatestReqDTO request) {
        SensorData data = influxDBUtils.queryLatestSensorData(
                influxDBConfig.getBucket(), request.getDeviceId());
        if (data == null) {
            return null;
        }
        return toResponse(data);
    }

    @Override
    public SensorData getLatestSensorDataRaw(String deviceId) {
        return influxDBUtils.queryLatestSensorData(influxDBConfig.getBucket(), deviceId);
    }

    /** 实体转响应 DTO */
    private SensorDataRespDTO toResponse(SensorData data) {
        SensorDataRespDTO response = new SensorDataRespDTO();
        response.setDeviceId(data.getDeviceId());
        response.setTemperatureAht(data.getTemperatureAht());
        response.setTemperatureBmp(data.getTemperatureBmp());
        response.setHumidity(data.getHumidity());
        response.setPressureHpa(data.getPressureHpa());
        response.setAltitudeM(data.getAltitudeM());
        response.setSensorStatus(data.getSensorStatus());
        response.setAht20Status(data.getAht20Status());
        response.setBmp280Status(data.getBmp280Status());
        response.setReportTime(data.getReportTime());
        return response;
    }
}
