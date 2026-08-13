package com.dboat.iot.dto.mqtt;

import lombok.Data;

/**
 * 各传感器独立状态
 *
 * @author dboat
 */
@Data
public class MqttUpSensorState {
    /** AHT20 温湿度传感器状态码 */
    private Integer aht20;
    /** BMP280 气压传感器状态码 */
    private Integer bmp280;
}
