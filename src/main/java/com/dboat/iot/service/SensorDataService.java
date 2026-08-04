package com.dboat.iot.service;

import com.dboat.iot.dto.request.SensorDataQueryReqDTO;
import com.dboat.iot.dto.response.SensorDataRespDTO;
import com.dboat.iot.entity.SensorData;

import java.util.List;

public interface SensorDataService {

    void saveSensorData(SensorData sensorData);

    List<SensorDataRespDTO> querySensorData(SensorDataQueryReqDTO request);

    SensorDataRespDTO getLatestSensorData(String deviceId);
}
