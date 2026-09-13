package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启用状态枚举
 * <p>适用于 {@code user_org.status}、{@code role.status}、{@code permission.status}、
 * {@code iot_dev_group.status} 等仅区分启用/禁用的状态字段。</p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum CommonStatusEnum implements CodeEnum {

    /** 禁用：不可用，鉴权与查询时需过滤 */
    DISABLED(0, "禁用", "Disabled"),

    /** 启用：正常可用 */
    ENABLED(1, "启用", "Enabled");

    /** 状态码值 */
    private final int code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;
}
