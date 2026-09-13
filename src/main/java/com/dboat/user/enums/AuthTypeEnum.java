package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户认证方式枚举
 * <p>对应 {@code user_auth.auth_type} 字段，一个用户可绑定多种登录方式。</p>
 * <ul>
 *   <li>{@link #PASSWORD} - 账号密码登录，credential 存 BCrypt 哈希</li>
 *   <li>{@link #SMS}      - 手机验证码登录，identifier 存手机号</li>
 *   <li>{@link #EMAIL}    - 邮箱验证码登录，identifier 存邮箱</li>
 *   <li>{@link #OAUTH}    - 第三方 OAuth 登录，credential 存第三方 token/openid</li>
 * </ul>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum AuthTypeEnum implements CodeEnum {

    /** 密码认证：identifier 为登录账号，credential 为 BCrypt 哈希 */
    PASSWORD(1, "密码", "Password"),

    /** 手机验证码认证：identifier 为手机号，无 credential */
    SMS(2, "手机验证码", "Sms Code"),

    /** 邮箱验证码认证：identifier 为邮箱，无 credential */
    EMAIL(3, "邮箱验证码", "Email Code"),

    /** 第三方 OAuth 认证：identifier 为 openid，credential 为第三方 token */
    OAUTH(4, "第三方授权", "OAuth");

    /** 认证方式码值，对应数据库 user_auth.auth_type */
    private final int code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;

    /**
     * 根据码值获取枚举实例
     *
     * @param code 认证方式码值
     * @return 对应枚举，未匹配返回 null
     */
    public static AuthTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AuthTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
