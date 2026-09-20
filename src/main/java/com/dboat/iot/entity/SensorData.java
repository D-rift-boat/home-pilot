package com.dboat.iot.entity;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 传感器遥测数据实体 —— 对应 InfluxDB Measurement: sensor_telemetry
 * <p>
 * 存储 ESP32-S3 设备每 5 秒上报一次的传感器采集数据。
 * 通过 InfluxDB Client 的 @Measurement 和 @Column 注解映射到 InfluxDB 时序数据库。
 * </p>
 * <p>
 * InfluxDB 数据模型设计：
 * <ul>
 *   <li>Bucket：iot</li>
 *   <li>Measurement：sensor_telemetry</li>
 *   <li>Tag（索引标签）：device_id —— 仅保留 device_id 为唯一 Tag，用于高效查询</li>
 *   <li>Field（测量字段）：温度、湿度、气压、海拔、各传感器状态码</li>
 *   <li>Timestamp：reportTime —— 数据上报时间戳</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Data
@Measurement(name = "sensor_telemetry")
public class SensorData {

    /**
     * 设备唯一标识（InfluxDB Tag，用于索引和过滤）
     * <p>对应 MQTT 主题 iot/telemetry/upload/{device_id} 中的设备ID</p>
     */
    @Column(tag = true)
    private String deviceId;

    /**
     * AHT20 温湿度传感器采集的温度值（单位：°C）
     */
    @Column
    private BigDecimal temperatureAht;

    /**
     * BMP280 气压传感器采集的温度值（单位：°C）
     * <p>可与 AHT20 温度做交叉校验</p>
     */
    @Column
    private BigDecimal temperatureBmp;

    /**
     * 环境湿度（单位：%RH），由 AHT20 传感器采集
     */
    @Column
    private BigDecimal humidity;

    /**
     * 大气压强（单位：hPa），由 BMP280 传感器采集
     */
    @Column
    private BigDecimal pressureHpa;

    /**
     * 海拔高度（单位：米），由 BMP280 根据气压计算得出
     * <p>负值表示低于海平面（如矿井、地下室场景）</p>
     */
    @Column
    private BigDecimal altitudeM;

    /**
     * 传感器整体连接状态码，对应 {@link com.dboat.iot.enums.SensorStatusEnum}
     * <p>1=正常, 2=失败, 3=部分失败</p>
     */
    @Column
    private Integer sensorStatus;

    /**
     * AHT20 温湿度传感器连接状态码
     * <p>1=正常, 2=失败, 3=部分失败</p>
     */
    @Column
    private Integer aht20Status;

    /**
     * BMP280 气压传感器连接状态码
     * <p>1=正常, 2=失败, 3=部分失败</p>
     */
    @Column
    private Integer bmp280Status;

    /**
     * 数据上报时间戳（InfluxDB 时间戳字段）
     * <p>精度为纳秒（NS），取设备上报时的系统时间</p>
     */
    @Column(timestamp = true)
    private Instant reportTime;
}
