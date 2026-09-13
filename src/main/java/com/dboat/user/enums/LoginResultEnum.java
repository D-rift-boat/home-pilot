package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录结果枚举
 * <p>对应 {@code auth_login_log.result} 字段，失败场景需同时记录 fail_reason。</p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum LoginResultEnum implements CodeEnum {

    /** 登录失败：密码错误 / 账号锁定 / 验证码错误 / 账号不存在等 */
    FAIL(0, "失败", "Fail"),

    /** 登录成功：已签发双令牌 */
    SUCCESS(1, "成功", "Success");

    /** 结果码值，对应数据库 auth_login_log.result */
    private final int code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;
}
