package com.dboat.user.dto.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 用户登录会话模型（Redis 存储结构）
 * <p>
 * 同一份 JSON 存储于两处：
 * <ul>
 *   <li>{@code auth:refresh:token:{refreshToken}} —— Refresh Token 反查会话，{@code used} 字段用于轮换重放检测</li>
 *   <li>{@code auth:session:{sessionId}} —— 会话详情，用于登出、踢人、会话列表展示</li>
 * </ul>
 * 字段名与 Lua 脚本 {@code LUA_REFRESH_CONSUME} 中引用的 userId / orgId / sessionId / used 严格对应，
 * 不可随意改名。
 * </p>
 *
 * @author dboat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionDTO implements Serializable {

    /** 会话ID，与 Access Token 的 jti 一致 */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 租户ID */
    private String orgId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 当前会话绑定的 Refresh Token */
    private String refreshToken;

    /** 登录时的角色编码快照 */
    private List<String> roleKeys;

    /** 登录时的令牌版本号快照 */
    private Integer tokenVersion;

    /** 登录时的权限版本号快照 */
    private Integer permVersion;

    /** 登录客户端IP */
    private String loginIp;

    /** 客户端 User-Agent */
    private String userAgent;

    /** 登录渠道：1Web 2App 3开放API */
    private Integer channel;

    /** 登录类型：1密码 2验证码 3第三方授权 */
    private Integer loginType;

    /** 登录时间戳（毫秒） */
    private Long loginTs;

    /** 会话过期时间戳（毫秒），与 Refresh Token TTL 对齐 */
    private Long expireTs;

    /**
     * Refresh Token 是否已被消费（轮换标记）
     * <p>0=未消费，1=已消费。已消费的令牌再次出现即判定为重放攻击。</p>
     */
    @Builder.Default
    private Integer used = 0;
}
