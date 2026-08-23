package com.dboat.iot.utils;

import com.dboat.iot.config.InfluxDBConfig;
import com.dboat.iot.entity.SensorData;
import com.influxdb.client.*;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.client.write.events.WriteErrorEvent;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class InfluxDBUtils {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBUtils.class);

    private static final String MEASUREMENT = "sensor_telemetry";

    private final InfluxDBClient influxDBClient;
    private final String org;

    public InfluxDBUtils(InfluxDBClient influxDBClient, InfluxDBConfig influxDBConfig) {
        this.influxDBClient = influxDBClient;
        this.org = influxDBConfig.getOrgName();
    }

    /**
     * Write sensor telemetry data to InfluxDB
     */
    public void writeSensorData(String bucket, SensorData sensorData) {
        Point point = Point.measurement(MEASUREMENT)
                .addTag("device_id", sensorData.getDeviceId())
                .addField("temperature_aht", toDouble(sensorData.getTemperatureAht()))
                .addField("temperature_bmp", toDouble(sensorData.getTemperatureBmp()))
                .addField("humidity", toDouble(sensorData.getHumidity()))
                .addField("pressure_hpa", toDouble(sensorData.getPressureHpa()))
                .addField("altitude_m", toDouble(sensorData.getAltitudeM()))
                .addField("sensor_status", safeInt(sensorData.getSensorStatus()))
                .addField("aht20_status", safeInt(sensorData.getAht20Status()))
                .addField("bmp280_status", safeInt(sensorData.getBmp280Status()))
                .time(sensorData.getReportTime(), WritePrecision.NS);

        WriteOptions options = WriteOptions.builder()
                .batchSize(200)
                .flushInterval(1000)
                .bufferLimit(5000)
                .maxRetries(3)
                .build();
        WriteApi writeApi = influxDBClient.getWriteApi(options);
        // 注册写入错误监听
        writeApi.listenEvents(WriteErrorEvent.class, event -> {
            Throwable throwable = event.getThrowable();
            log.error("InfluxDB写入事件异常", throwable);
        });
        writeApi.writePoint(bucket, org, point);
        log.debug("Async write sensor telemetry point for device: {}", sensorData.getDeviceId());
    }

    /**
     * Query sensor data by device ID and time range
     */
    public List<SensorData> querySensorData(String bucket, String deviceId, Instant start, Instant end) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %s, stop: %s) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")",
                bucket, start.toString(), end.toString(), MEASUREMENT, deviceId
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);

        List<SensorData> result = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                SensorData data = mapRecordToSensorData(record, deviceId);
                result.add(data);
            }
        }
        return result;
    }

    /**
     * Query the latest sensor data for a device
     */
    public SensorData queryLatestSensorData(String bucket, String deviceId) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -30d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                "|> last()",
                bucket, MEASUREMENT, deviceId
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);

        for (FluxTable table : tables) {
            List<FluxRecord> records = table.getRecords();
            if (!records.isEmpty()) {
                return mapRecordToSensorData(records.getFirst(), deviceId);
            }
        }
        return null;
    }

    /**
     * Map FluxRecord to SensorData
     */
    private SensorData mapRecordToSensorData(FluxRecord record, String deviceId) {
        SensorData data = new SensorData();
        data.setDeviceId(deviceId);
        data.setReportTime(record.getTime());

        if (record.getValueByKey("temperature_aht") != null) {
            data.setTemperatureAht(toBigDecimal(record.getValueByKey("temperature_aht")));
        }
        if (record.getValueByKey("temperature_bmp") != null) {
            data.setTemperatureBmp(toBigDecimal(record.getValueByKey("temperature_bmp")));
        }
        if (record.getValueByKey("humidity") != null) {
            data.setHumidity(toBigDecimal(record.getValueByKey("humidity")));
        }
        if (record.getValueByKey("pressure_hpa") != null) {
            data.setPressureHpa(toBigDecimal(record.getValueByKey("pressure_hpa")));
        }
        if (record.getValueByKey("altitude_m") != null) {
            data.setAltitudeM(toBigDecimal(record.getValueByKey("altitude_m")));
        }
        if (record.getValueByKey("sensor_status") != null) {
            data.setSensorStatus(((Number) record.getValueByKey("sensor_status")).intValue());
        }
        if (record.getValueByKey("aht20_status") != null) {
            data.setAht20Status(((Number) record.getValueByKey("aht20_status")).intValue());
        }
        if (record.getValueByKey("bmp280_status") != null) {
            data.setBmp280Status(((Number) record.getValueByKey("bmp280_status")).intValue());
        }
        return data;
    }

    private double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
