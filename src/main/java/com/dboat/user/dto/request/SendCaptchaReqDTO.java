package com.dboat.user.dto.request;

import com.dboat.iot.dto.request.BaseReqDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 发送验证码请求 DTO
 * <p>
 * 支持注册、验证码登录、重置密码、修改密码四类场景，
 * 场景之间验证码相互隔离，且同一目标存在发送间隔限制防刷。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "发送验证码请求")
public class SendCaptchaReqDTO extends BaseReqDTO {

    /**
     * 业务场景：register / login / resetPwd / changePwd
     */
    @NotBlank(message = "验证码场景不能为空")
    @Schema(description = "业务场景：register=注册 login=登录 resetPwd=重置密码 changePwd=修改密码",
            example = "register", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scene;

    /**
     * 目标标识：手机号或邮箱
     */
    @NotBlank(message = "接收验证码的手机号/邮箱不能为空")
    @Size(max = 128, message = "目标标识长度不能超过128")
    @Schema(description = "接收验证码的手机号或邮箱", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;
}
