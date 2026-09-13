package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户账号状态枚举
 * <p>对应 {@code user.status} 字段，登录前必须校验账号状态。</p>
 * <ul>
 *   <li>{@link #DISABLED} - 已禁用，管理员封禁，禁止登录</li>
 *   <li>{@link #NORMAL}   - 正常，允许登录</li>
 *   <li>{@link #LOCKED}   - 已锁定，密码连续错误触发，到期自动解锁</li>
 * </ul>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum UserStatusEnum implements CodeEnum {

    /** 禁用：管理员主动封禁，需人工解锁 */
    DISABLED(0, "禁用", "Disabled"),

    /** 正常：允许登录 */
    NORMAL(1, "正常", "Normal"),

    /** 锁定：连续登录失败触发，lock_expire_time 到期后自动解锁 */
    LOCKED(2, "锁定", "Locked");

    /** 状态码值，对应数据库 user.status */
    private final int code;

    /** 中文名称，用于日志和页面展示 */
    private final String nameCn;

    /** 英文名称，用于国际化场景 */
    private final String nameEn;

    /**
     * 根据码值获取枚举实例
     *
     * @param code 状态码值
     * @return 对应枚举，未匹配返回 null
     */
    public static UserStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
