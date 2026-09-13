package com.dboat.user.dto.request;

import com.dboat.iot.dto.request.BaseReqDTO;
import com.dboat.user.common.constants.AuthConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户注册请求 DTO
 * <p>
 * 注册流程：手机号/邮箱 + 验证码 + 密码 → 校验验证码 → 创建用户（默认角色 VIEWER）
 * → 若为平台首个用户则自动创建租户并授予 ADMIN → 签发双令牌自动登录。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户注册请求")
public class RegisterReqDTO extends BaseReqDTO {

    /**
     * 注册标识：手机号或邮箱，同时作为登录账号（username 为空时）
     */
    @NotBlank(message = "注册标识（手机号/邮箱）不能为空")
    @Size(max = 128, message = "注册标识长度不能超过128")
    @Schema(description = "注册标识：手机号或邮箱", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;

    /**
     * 验证码，由 /api/auth/captcha/send 下发
     */
    @NotBlank(message = "验证码不能为空")
    @Size(max = 16, message = "验证码长度不合法")
    @Schema(description = "短信/邮箱验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String captchaCode;

    /**
     * 登录密码，强度要求：≥8 位，含大小写字母 + 数字 + 特殊符号
     */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = AuthConstants.PASSWORD_STRENGTH_REGEX, message = AuthConstants.PASSWORD_STRENGTH_MSG)
    @Schema(description = "登录密码（≥8位，含大小写字母+数字+符号）", example = "Abcd@1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    /**
     * 登录账号，为空时默认使用 identifier
     */
    @Size(max = 128, message = "账号长度不能超过128")
    @Schema(description = "登录账号，为空时默认使用注册标识", example = "dboat")
    private String username;

    /**
     * 用户昵称
     */
    @Size(max = 64, message = "昵称长度不能超过64")
    @Schema(description = "用户昵称", example = "船长")
    private String nickname;

    /**
     * 加入的租户ID。为空且平台无任何用户时自动创建租户并授予 ADMIN；
     * 为空但平台已存在用户时，注册失败并提示需要租户邀请。
     */
    @Size(max = 64, message = "租户ID长度不能超过64")
    @Schema(description = "加入的租户ID，平台首个用户可不传（自动建租户）")
    private String orgId;

    /**
     * 自动创建租户时使用的租户名称
     */
    @Size(max = 128, message = "租户名称长度不能超过128")
    @Schema(description = "自动创建租户时的租户名称", example = "默认租户")
    private String orgName;

    /**
     * 登录渠道：1Web 2App 3开放API，默认 1
     */
    @Schema(description = "登录渠道：1Web 2App 3开放API", example = "1", defaultValue = "1")
    private Integer channel;
}
