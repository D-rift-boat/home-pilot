package com.dboat.iot.enums.webhook;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * webhook事件类型枚举
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum WebHookEventTypeEnum{

    /** 设备上线：传感器数据上报时记录 */
    CONNECTED("CONNECTED", "设备连接", "Device Online"),

    /** 设备离线：EMQX 设备 MQTT 连接断开 */
    DISCONNECTED("DISCONNECTED", "设备断连", "Device Offline"),

    /** 设备异常：传感器状态码异常 */
    ABNORMAL("ABNORMAL", "设备异常", "Device Abnormal");

    /** 类型码值，用于数据库存储 */
    private final String code;

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
    public static WebHookEventTypeEnum fromCode(String code) {
        for (WebHookEventTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown WebHookEventType code: " + code);
    }
}
