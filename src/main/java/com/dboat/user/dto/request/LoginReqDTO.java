package com.dboat.user.dto.request;

import com.dboat.iot.dto.request.BaseReqDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户登录请求 DTO
 * <p>
 * 登录流程：限流校验 → 验证码校验（可选） → 账号状态检查（锁定/禁用）
 * → 密码比对（失败计数 +1，≥5 次锁 10 分钟） → 签发双令牌 → 写入 Redis 会话
 * → 会话并发控制 → 异步写登录日志。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户登录请求")
public class LoginReqDTO extends BaseReqDTO {

    /**
     * 登录类型：1密码登录 2验证码登录，默认 1
     */
    @Schema(description = "登录类型：1密码 2验证码 3第三方授权", example = "1", defaultValue = "1")
    private Integer loginType;

    /**
     * 登录标识：账号 / 手机号 / 邮箱
     */
    @NotBlank(message = "登录账号不能为空")
    @Size(max = 128, message = "登录账号长度不能超过128")
    @Schema(description = "登录标识：账号/手机号/邮箱", example = "dboat", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;

    /**
     * 登录密码，loginType=1 时必填（BCrypt 明文，服务端比对哈希）
     */
    @Size(max = 128, message = "密码长度不合法")
    @Schema(description = "登录密码，密码登录时必填", example = "Abcd@1234")
    private String password;

    /**
     * 验证码，loginType=2 时必填
     */
    @Size(max = 16, message = "验证码长度不合法")
    @Schema(description = "短信/邮箱验证码，验证码登录时必填", example = "123456")
    private String captchaCode;

    /**
     * 租户ID。多租户下同一 identifier 可能存在于不同租户，传此字段可精确定位；
     * 为空时按 identifier 全局唯一匹配，匹配到多个则提示需要指定租户。
     */
    @Size(max = 64, message = "租户ID长度不能超过64")
    @Schema(description = "租户ID，跨租户同名账号时必填")
    private String orgId;

    /**
     * 登录渠道：1Web 2App 3开放API，默认 1
     */
    @Schema(description = "登录渠道：1Web 2App 3开放API", example = "1", defaultValue = "1")
    private Integer channel;
}
