package com.dboat.iot.dto.mqtt;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 环境监测核心采集数据
 *
 * @author dboat
 */
@Data
public class MqttUpEnvData {
    /** AHT20 采集温度（°C） */
    private BigDecimal tempAht;
    /** BMP280 采集温度（°C） */
    private BigDecimal tempBmp;
    /** 环境湿度（%RH） */
    private BigDecimal humidity;
    /** 大气压强（hPa） */
    private BigDecimal pressureHpa;
    /** 海拔高度（米） */
    private BigDecimal altitude;
}
