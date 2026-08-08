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

@Service
public class SensorDataServiceImpl implements SensorDataService {

    private static final Logger log = LoggerFactory.getLogger(SensorDataServiceImpl.class);

    private final InfluxDBUtils influxDBUtils;
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
        log.debug("Saved sensor data for device: {}", sensorData.getDeviceId());
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

    private SensorDataRespDTO toResponse(SensorData data) {
        SensorDataRespDTO response = new SensorDataRespDTO();
        response.setDeviceId(data.getDeviceId());
        response.setTemp(data.getTemp());
        response.setHumi(data.getHumi());
        response.setPress(data.getPress());
        response.setReportTime(data.getReportTime());
        return response;
    }
}
