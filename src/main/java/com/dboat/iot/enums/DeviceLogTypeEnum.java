package com.dboat.iot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备日志类型枚举
 * <p>
 * 定义系统中设备日志的事件类型标识，用于 device_log 表的 log_type 字段。
 * 日志记录场景：
 * <ul>
 *   <li>{@link #ONLINE}    - 设备上线，传感器数据首次上报时记录</li>
 *   <li>{@link #OFFLINE}   - 设备离线，由 EMQX 系统事件触发</li>
 *   <li>{@link #ABNORMAL}  - 设备异常，传感器状态码异常时记录</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum DeviceLogTypeEnum implements CodeEnum {

    /** 设备上线：传感器数据上报时记录 */
    ONLINE(1, "设备上线", "Device Online"),

    /** 设备离线：EMQX 检测到设备 MQTT 连接断开 */
    OFFLINE(2, "设备离线", "Device Offline"),

    /** 设备异常：传感器状态码异常 */
    ABNORMAL(3, "设备异常", "Device Abnormal");

    /** 类型码值，用于数据库存储 */
    private final int code;

    /** 中文名称，用于页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化和日志 */
    private final String nameEn;

    /**
     * 根据码值获取对应的日志类型枚举
     *
     * @param code 类型码值（1/2/3）
     * @return 对应的日志类型枚举实例
     * @throws IllegalArgumentException 如果码值无法匹配任何枚举
     */
    public static DeviceLogTypeEnum fromCode(int code) {
        for (DeviceLogTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown DeviceLogType code: " + code);
    }
}
