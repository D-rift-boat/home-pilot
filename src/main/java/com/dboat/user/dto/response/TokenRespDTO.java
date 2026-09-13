package com.dboat.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 双令牌响应 DTO
 * <p>
 * 登录 / 注册 / 刷新令牌成功后统一返回此结构：
 * <ul>
 *   <li>accessToken：JWT（RS256 签名），无状态，请求时置于 {@code Authorization: Bearer xxx}</li>
 *   <li>refreshToken：随机串，Redis 有状态，可撤销可轮换，仅用于换取新的双令牌</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Data
@Builder
@Schema(description = "双令牌响应")
public class TokenRespDTO implements Serializable {

    /** Access Token（JWT，RS256） */
    @Schema(description = "Access Token（JWT）")
    private String accessToken;

    /** Refresh Token（随机串，一次性，刷新后轮换） */
    @Schema(description = "Refresh Token，刷新后旧值立即作废")
    private String refreshToken;

    /** 令牌类型，固定 Bearer */
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    /** Access Token 有效期（秒） */
    @Schema(description = "Access Token 有效期（秒）", example = "7200")
    private Long expiresIn;

    /** Refresh Token 有效期（秒） */
    @Schema(description = "Refresh Token 有效期（秒）", example = "604800")
    private Long refreshExpiresIn;

    /** 会话ID，与 JWT 的 jti 一致，登出时用于定位会话 */
    @Schema(description = "会话ID")
    private String sessionId;

    /** 用户ID */
    @Schema(description = "用户ID")
    private String userId;

    /** 租户ID */
    @Schema(description = "租户ID")
    private String orgId;

    /** 登录账号 */
    @Schema(description = "登录账号")
    private String username;

    /** 用户昵称 */
    @Schema(description = "用户昵称")
    private String nickname;

    /** 角色编码集合 */
    @Schema(description = "角色编码集合")
    private List<String> roleKeys;

    /** 是否首次登录（true 时前端应强制跳转改密页） */
    @Schema(description = "是否首次登录，true 需强制修改密码")
    private Boolean firstLogin;
}
