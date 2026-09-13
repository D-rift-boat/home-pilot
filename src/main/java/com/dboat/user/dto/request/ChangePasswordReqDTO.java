package com.dboat.user.dto.request;

import com.dboat.iot.dto.request.BaseReqDTO;
import com.dboat.user.common.constants.AuthConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改密码请求 DTO
 * <p>
 * 改密成功后自增 {@code user_auth.password_version} 与 {@code user.token_version}，
 * 使该用户所有已签发的 Access Token 与 Refresh Token 立即失效，强制全端重新登录。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "修改密码请求")
public class ChangePasswordReqDTO extends BaseReqDTO {

    /**
     * 原密码
     */
    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;

    /**
     * 新密码，强度要求：≥8 位，含大小写字母 + 数字 + 特殊符号
     */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = AuthConstants.PASSWORD_STRENGTH_REGEX, message = AuthConstants.PASSWORD_STRENGTH_MSG)
    @Schema(description = "新密码（≥8位，含大小写字母+数字+符号）", example = "Abcd@5678", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
