package com.dboat.iot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 传感器连接状态枚举
 * <p>
 * 用于描述设备上报数据中各传感器模块的连接/工作状态。
 * 对应 ESP32 上报 UP_DATA 消息中 payload.sensorStatus 对象内的状态码字段：
 * <pre>
 * {
 *   "payload": {
 *     "sensorStatus": {
 *       "aht20": 1,
 *       "bmp280": 1
 *     }
 *   }
 * }
 * </pre>
 * 状态码说明：1=正常, 2=连接失败, 3=部分失败
 * </p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum SensorStatusEnum implements CodeEnum {

    /** 正常：传感器连接正常，数据有效 */
    NORMAL(1, "正常", "Normal"),

    /** 失败：传感器连接失败，无法获取数据 */
    FAILURE(2, "失败", "Failure"),

    /** 部分失败：传感器部分功能异常，数据可能不准确 */
    PARTIAL_FAILURE(3, "部分失败", "Partial Failure");

    /** 状态码值，对应上报 JSON 中的状态字段 */
    private final int code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;

    /**
     * 根据码值获取对应的枚举实例
     *
     * @param code 状态码值（1/2/3）
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 如果码值无法匹配任何枚举
     */
    public static SensorStatusEnum fromCode(int code) {
        for (SensorStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown SensorStatus code: " + code);
    }

    /**
     * 判断指定码值是否为异常状态（失败或部分失败）
     * <p>
     * 在流式告警计算中，当传感器状态码为 2 或 3 时触发传感器故障告警。
     * </p>
     *
     * @param code 传感器状态码
     * @return true=异常状态（需要告警），false=正常
     */
    public static boolean isAbnormal(int code) {
        return code == FAILURE.code || code == PARTIAL_FAILURE.code;
    }
}
