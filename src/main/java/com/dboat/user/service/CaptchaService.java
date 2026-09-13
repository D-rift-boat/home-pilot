package com.dboat.user.service;

import com.dboat.user.dto.response.CaptchaRespDTO;

/**
 * 验证码服务
 * <p>
 * 覆盖注册、验证码登录、重置密码、修改密码四类场景，场景之间验证码互相隔离
 * （Redis Key 以 scene 区分），避免注册验证码被复用于其他敏感操作。
 * </p>
 * <p>
 * 安全约束：
 * <ul>
 *   <li>发送间隔防刷：同一 scene + identifier 在 {@code auth.captcha.send-interval-seconds} 内只允许发送一次</li>
 *   <li>一次性消费：校验通过立即删除，杜绝重放</li>
 *   <li>校验原子化：读取-比对-删除由 Lua 脚本一次完成，避免并发下同一验证码被多次使用</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
public interface CaptchaService {

    /**
     * 发送验证码
     *
     * @param scene      业务场景标识（register / login / resetPwd / changePwd）
     * @param identifier 接收目标：手机号或邮箱
     * @return 发送结果（含脱敏目标、有效期，开发环境附带验证码明文）
     */
    CaptchaRespDTO sendCaptcha(String scene, String identifier);

    /**
     * 校验验证码（一次性，校验通过即失效）
     *
     * @param scene      业务场景标识
     * @param identifier 接收目标
     * @param code       用户提交的验证码
     * @return true=校验通过
     */
    boolean verify(String scene, String identifier, String code);

    /**
     * 主动作废验证码
     * <p>注册成功、密码修改成功后调用，避免同一验证码在有效期内被二次利用。</p>
     *
     * @param scene      业务场景标识
     * @param identifier 接收目标
     */
    void invalidate(String scene, String identifier);
}
