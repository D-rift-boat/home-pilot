package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录类型枚举
 * <p>对应 {@code auth_login_log.login_type} 字段，标识本次登录采用的认证手段。</p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum LoginTypeEnum implements CodeEnum {

    /** 账号密码登录 */
    PASSWORD(1, "密码登录", "Password"),

    /** 短信/邮箱验证码登录 */
    CAPTCHA(2, "验证码登录", "Captcha"),

    /** 第三方 OAuth 授权登录 */
    OAUTH(3, "第三方授权登录", "OAuth");

    /** 登录类型码值，对应数据库 auth_login_log.login_type */
    private final int code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;

    /**
     * 根据码值获取枚举实例，未匹配时返回默认 {@link #PASSWORD}
     *
     * @param code 登录类型码值
     * @return 对应枚举
     */
    public static LoginTypeEnum fromCode(Integer code) {
        if (code == null) {
            return PASSWORD;
        }
        for (LoginTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return PASSWORD;
    }
}
