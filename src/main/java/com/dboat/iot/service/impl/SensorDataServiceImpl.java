package com.dboat.iot.service.impl;

import com.dboat.iot.common.constants.InfluxMeasurementConst;
import com.dboat.iot.config.InfluxDBConfig;
import com.dboat.iot.dto.request.SensorDataLatestReqDTO;
import com.dboat.iot.dto.request.SensorDataQueryReqDTO;
import com.dboat.iot.dto.response.SensorDataRespDTO;
import com.dboat.iot.entity.SensorData;
import com.dboat.iot.service.SensorDataService;
import com.dboat.iot.utils.InfluxDBUtils;
import com.influxdb.exceptions.InfluxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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
                ? Instant.ofEpochMilli(request.getStartTime())
                : Instant.now().minusSeconds(3600); // Default: last 1 hour
        Instant end = request.getEndTime() != null
                ? Instant.ofEpochMilli(request.getEndTime())
                : Instant.now();

        // 1.计算查询时间跨度
        Duration querySpan = Duration.between(start, end);
        String selectedMeasurement;

        // 按照你的表格规则自动选择bucket + measurement
        if (querySpan.toHours() <= 2) {
            selectedMeasurement = InfluxMeasurementConst.RAW_SENSOR_TELEMETRY;
        } else if (querySpan.toDays() <= 2) {
            selectedMeasurement = InfluxMeasurementConst.AGG_ENV_METRIC_5MIN;
        } else if (querySpan.toDays() <= 30) {
            selectedMeasurement = InfluxMeasurementConst.AGG_ENV_METRIC_1H;
        } else {
            selectedMeasurement = InfluxMeasurementConst.AGG_ENV_METRIC_1D;
        }
        List<SensorData> dataList = null;
        try {
            dataList = influxDBUtils.querySensorData(
                    influxDBConfig.getBucket(),
                    request.getDeviceId(),
                    start,
                    end,
                    selectedMeasurement
            );
        } catch (InfluxException e) {
            // === InfluxDB全部异常：连接失败、超时、bucket不存在、权限、语法错误全部走到这里 ===
            // 打印完整error日志，便于告警排查，必须打印参数：bucket、measurement、deviceId、时间区间
            log.error("InfluxDB查询异常,bucket={},measurement={},deviceId={},start={},end={}",
                    influxDBConfig.getBucket(), selectedMeasurement, request.getDeviceId(), start, end, e);
            // 向上抛出，交给GlobalExceptionHandler处理，不要吞异常返回空List！！
            throw e;
        }

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
