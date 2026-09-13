package com.dboat.user.dto.request;

import com.dboat.iot.dto.request.BaseReqDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 刷新令牌请求 DTO
 * <p>
 * Refresh Token 轮换策略：每次刷新发放新的 Refresh Token 并作废旧值；
 * 若检测到已作废的旧 Refresh Token 被重放，判定令牌泄漏，立即撤销该用户全部会话。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "刷新令牌请求")
public class RefreshTokenReqDTO extends BaseReqDTO {

    /**
     * 待轮换的 Refresh Token
     */
    @NotBlank(message = "refreshToken 不能为空")
    @Size(max = 256, message = "refreshToken 长度不合法")
    @Schema(description = "Refresh Token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    /**
     * 登录渠道：1Web 2App 3开放API，默认沿用原会话渠道
     */
    @Schema(description = "登录渠道：1Web 2App 3开放API", example = "1")
    private Integer channel;
}
