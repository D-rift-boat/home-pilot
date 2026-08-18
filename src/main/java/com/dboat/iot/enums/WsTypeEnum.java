package com.dboat.iot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ws 类型枚举
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum WsTypeEnum {

    /** 用户在线设备数量 */
    USER_DEVICE_ONLINE_COUNT("USER_DEVICE_ONLINE_COUNT", "用户在线设备数量", "USER_DEVICE_ONLINE_COUNT");

    /** 状态码值，用于 Redis 存储和接口传输 */
    private final String code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;

    /**
     * 根据码值获取对应的枚举实例
     *
     * @param code 状态码值（0/1/2）
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 如果码值无法匹配任何枚举
     */
    public static WsTypeEnum fromCode(int code) {
        for (WsTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown DeviceOnlineStatus code: " + code);
    }
}
