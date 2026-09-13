package com.dboat.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 验证码发送结果响应 DTO
 * <p>
 * 出于安全考虑默认不回传验证码明文；仅在 {@code auth.captcha.mock-enabled=true}
 * 的开发环境下通过 {@code mockCode} 返回，便于本地联调。
 * </p>
 *
 * @author dboat
 */
@Data
@Builder
@Schema(description = "验证码发送结果")
public class CaptchaRespDTO implements Serializable {

    /** 业务场景标识 */
    @Schema(description = "业务场景标识", example = "register")
    private String scene;

    /** 脱敏后的目标标识，如 138****8000 */
    @Schema(description = "脱敏后的接收目标", example = "138****8000")
    private String identifier;

    /** 验证码有效期（秒） */
    @Schema(description = "验证码有效期（秒）", example = "300")
    private Long expireSeconds;

    /** 下一次可发送的间隔（秒） */
    @Schema(description = "重新发送间隔（秒）", example = "60")
    private Long sendIntervalSeconds;

    /** 开发环境模拟下发的验证码明文，生产环境为 null */
    @Schema(description = "模拟验证码（仅开发环境返回）", example = "123456")
    private String mockCode;
}
