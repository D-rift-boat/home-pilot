package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证码业务场景枚举
 * <p>
 * 不同场景的验证码相互隔离（Redis Key 中以 scene 区分），
 * 避免注册验证码被用于重置密码等其他场景。
 * </p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum CaptchaSceneEnum implements CodeEnum {

    /** 注册场景：校验手机号/邮箱归属 */
    REGISTER(1, "register", "注册验证码"),

    /** 登录场景：验证码登录 */
    LOGIN(2, "login", "登录验证码"),

    /** 重置密码场景：忘记密码 */
    RESET_PASSWORD(3, "resetPwd", "重置密码验证码"),

    /** 修改密码场景：已登录状态下二次校验 */
    CHANGE_PASSWORD(4, "changePwd", "修改密码验证码");

    /** 场景码值 */
    private final int code;

    /** 场景标识，作为 Redis Key 的一部分 */
    private final String scene;

    /** 场景中文描述 */
    private final String nameCn;

    /**
     * 英文名称，返回场景标识本身
     *
     * @return 场景标识
     */
    @Override
    public String getNameEn() {
        return scene;
    }

    /**
     * 根据场景标识获取枚举实例
     *
     * @param scene 场景标识（如 register）
     * @return 对应枚举，未匹配返回 null
     */
    public static CaptchaSceneEnum fromScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return null;
        }
        for (CaptchaSceneEnum e : values()) {
            if (e.scene.equalsIgnoreCase(scene)) {
                return e;
            }
        }
        return null;
    }
}
