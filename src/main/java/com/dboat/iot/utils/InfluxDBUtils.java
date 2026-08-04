package com.dboat.iot.utils;

import com.dboat.iot.config.InfluxDBConfig;
import com.dboat.iot.entity.SensorData;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
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

    private final InfluxDBClient influxDBClient;
    private final String org;

    public InfluxDBUtils(InfluxDBClient influxDBClient, InfluxDBConfig influxDBConfig) {
        this.influxDBClient = influxDBClient;
        this.org = influxDBConfig.getOrg();
    }

    /**
     * Write sensor data to InfluxDB
     */
    public void writeSensorData(String bucket, SensorData sensorData) {
        Point point = Point.measurement("sensor")
                .addTag("device_id", sensorData.getDeviceId())
                .addField("temp", sensorData.getTemp())
                .addField("humi", sensorData.getHumi())
                .addField("press", sensorData.getPress())
                .time(sensorData.getReportTime(), WritePrecision.NS);

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writePoint(bucket, org, point);
        log.debug("Written sensor data point for device: {}", sensorData.getDeviceId());
    }

    /**
     * Query sensor data by device ID and time range
     */
    public List<SensorData> querySensorData(String bucket, String deviceId, Instant start, Instant end) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %s, stop: %s) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")",
                bucket, start.toString(), end.toString(), deviceId
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);

        List<SensorData> result = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                SensorData data = new SensorData();
                data.setDeviceId(deviceId);
                data.setReportTime(record.getTime());
                if (record.getValueByKey("temp") != null) {
                    data.setTemp(new BigDecimal(record.getValueByKey("temp").toString()));
                }
                if (record.getValueByKey("humi") != null) {
                    data.setHumi(new BigDecimal(record.getValueByKey("humi").toString()));
                }
                if (record.getValueByKey("press") != null) {
                    data.setPress(((Number) record.getValueByKey("press")).longValue());
                }
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
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor\") " +
                "|> filter(fn: (r) => r[\"device_id\"] == \"%s\") " +
                "|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
                "|> last()",
                bucket, deviceId
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);

        for (FluxTable table : tables) {
            List<FluxRecord> records = table.getRecords();
            if (!records.isEmpty()) {
                FluxRecord record = records.getFirst();
                SensorData data = new SensorData();
                data.setDeviceId(deviceId);
                data.setReportTime(record.getTime());
                if (record.getValueByKey("temp") != null) {
                    data.setTemp(new BigDecimal(record.getValueByKey("temp").toString()));
                }
                if (record.getValueByKey("humi") != null) {
                    data.setHumi(new BigDecimal(record.getValueByKey("humi").toString()));
                }
                if (record.getValueByKey("press") != null) {
                    data.setPress(((Number) record.getValueByKey("press")).longValue());
                }
                return data;
            }
        }
        return null;
    }
}
