package com.dboat.iot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备在线状态枚举
 * <p>
 * 用于描述设备当前的网络连接状态，存储在 Redis 的 Hash 字段中（key: iot:device:state:{device_id}）。
 * 该状态由 MQTT 消息处理逻辑和 EMQX 系统事件实时维护，不存储在 MySQL 中。
 * </p>
 * <ul>
 *   <li>{@link #OFFLINE} - 设备离线，长时间无数据上报或收到 EMQX 断连事件</li>
 *   <li>{@link #ONLINE}  - 设备在线，最近一次数据上报正常</li>
 *   <li>{@link #ABNORMAL} - 设备异常，传感器故障或其他异常情况</li>
 * </ul>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum DeviceOnlineStatusEnum implements CodeEnum {

    /** 离线：设备未连接或已断连 */
    OFFLINE(0, "离线", "Offline"),

    /** 在线：设备正常连接并上报数据 */
    ONLINE(1, "在线", "Online"),

    /** 异常：设备存在传感器故障或其他异常情况 */
    ABNORMAL(2, "异常", "Abnormal");

    /** 状态码值，用于 Redis 存储和接口传输 */
    private final int code;

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
    public static DeviceOnlineStatusEnum fromCode(int code) {
        for (DeviceOnlineStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown DeviceOnlineStatus code: " + code);
    }
}
