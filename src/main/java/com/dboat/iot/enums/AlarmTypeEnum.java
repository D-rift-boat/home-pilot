package com.dboat.iot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 告警类型枚举
 * <p>
 * 定义系统中所有告警场景的类型标识，用于 device_alarm_log 表的 alarm_type 字段。
 * 告警触发场景：
 * <ul>
 *   <li>{@link #SENSOR_FAULT}    - 传感器上报数据中状态码异常（2=失败/3=部分失败），由流式告警引擎在数据入库前检测</li>
 *   <li>{@link #DEVICE_OFFLINE}  - 设备断连，由 EMQX 系统事件 $SYS/brokers/+/clients/+/disconnected 触发</li>
 *   <li>{@link #DEVICE_ABNORMAL} - 设备运行异常（预留，用于扩展更多异常场景）</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum AlarmTypeEnum implements CodeEnum {

    /** 传感器故障：AHT20/BMP280 等传感器连接失败或部分失败 */
    SENSOR_FAULT(1, "传感器故障", "Sensor Fault"),

    /** 设备离线：EMQX 检测到设备 MQTT 连接断开 */
    DEVICE_OFFLINE(2, "设备离线", "Device Offline"),

    /** 设备异常：设备运行状态异常（预留扩展） */
    DEVICE_ABNORMAL(3, "设备异常", "Device Abnormal");

    /** 告警类型码值，用于数据库存储 */
    private final int code;

    /** 中文名称，用于告警通知和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化和日志 */
    private final String nameEn;

    /**
     * 根据码值获取对应的告警类型枚举
     *
     * @param code 告警类型码值（1/2/3）
     * @return 对应的告警类型枚举实例
     * @throws IllegalArgumentException 如果码值无法匹配任何枚举
     */
    public static AlarmTypeEnum fromCode(int code) {
        for (AlarmTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown AlarmType code: " + code);
    }
}
