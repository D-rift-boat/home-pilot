package com.dboat.user.dto.request;

import com.dboat.iot.dto.request.BaseReqDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登出请求 DTO
 * <p>
 * 默认登出当前 Access Token 对应的会话；显式传入 refreshToken 时可同时作废指定 Refresh Token。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "登出请求")
public class LogoutReqDTO extends BaseReqDTO {

    /**
     * 需要一并作废的 Refresh Token，可为空（服务端按当前会话反查）
     */
    @Size(max = 256, message = "refreshToken 长度不合法")
    @Schema(description = "需要作废的 Refresh Token，可不传")
    private String refreshToken;
}
