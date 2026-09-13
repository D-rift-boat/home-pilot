package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录渠道枚举
 * <p>对应 {@code auth_login_log.channel} 字段，用于区分登录来源做风控与审计。</p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum LoginChannelEnum implements CodeEnum {

    /** Web 管理后台登录 */
    WEB(1, "Web端", "Web"),

    /** 移动 App 登录 */
    APP(2, "App端", "App"),

    /** 开放 API / 第三方系统登录 */
    OPEN_API(3, "开放API", "Open API");

    /** 渠道码值，对应数据库 auth_login_log.channel */
    private final int code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;

    /**
     * 根据码值获取枚举实例，未匹配时返回默认 {@link #WEB}
     *
     * @param code 渠道码值
     * @return 对应枚举
     */
    public static LoginChannelEnum fromCode(Integer code) {
        if (code == null) {
            return WEB;
        }
        for (LoginChannelEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return WEB;
    }
}
