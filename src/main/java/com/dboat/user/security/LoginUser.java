package com.dboat.user.security;

import com.dboat.user.common.constants.AuthConstants;
import com.dboat.user.enums.UserStatusEnum;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring Security 登录用户主体
 * <p>
 * 承载认证通过后的用户上下文：用户ID、租户ID、令牌/权限版本、角色集合与权限集合。
 * 权限模型采用「RBAC 全局权限 + 设备分组细粒度权限」叠加设计，
 * 此处仅装载 RBAC 部分（{@code role.role_code} 与 {@code permission.perm_code}）。
 * </p>
 * <p>
 * GrantedAuthority 生成规则：
 * <ul>
 *   <li>角色 → {@code ROLE_ + roleCode}，供 {@code hasRole('ADMIN')} 使用</li>
 *   <li>权限 → 原始 {@code permCode}，供 {@code hasAuthority('iot:device:read')} 使用</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Data
@Builder
public class LoginUser implements UserDetails, Serializable {

    /** 用户ID（user.user_id） */
    private String userId;

    /** 租户ID（user.org_id），多租户数据隔离核心 */
    private String orgId;

    /** 登录账号（user.username） */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 密码 BCrypt 哈希，仅密码登录校验时使用，JWT 过滤器链路中为 null */
    private String password;

    /** 账号状态：0禁用 1正常 2锁定 */
    private Integer status;

    /** 令牌版本号，与 JWT 声明比对，不一致即判定令牌失效 */
    private Integer tokenVersion;

    /** 权限版本号（user_auth.password_version），用于权限缓存失效判定 */
    private Integer permVersion;

    /** 首次登录标记：1=首次登录（需强制改密） 0=非首次 */
    private Integer firstLogin;

    /** 会话ID，与 Access Token 的 jti 一致；JWT 过滤器链路中由令牌解析得到 */
    private String sessionId;

    /** 角色编码集合 */
    @Builder.Default
    private Set<String> roleKeys = new LinkedHashSet<>();

    /** 权限编码集合 */
    @Builder.Default
    private Set<String> perms = new LinkedHashSet<>();

    /** 登录时间戳（毫秒） */
    private Long loginTs;

    /** 登录客户端IP */
    private String loginIp;

    /**
     * 构建 Spring Security 权限集合
     * <p>角色加 {@code ROLE_} 前缀，权限保持原始编码。</p>
     *
     * @return 权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roleKeys != null) {
            for (String roleCode : roleKeys) {
                authorities.add(new SimpleGrantedAuthority(AuthConstants.ROLE_AUTHORITY_PREFIX + roleCode));
            }
        }
        if (perms != null) {
            for (String permCode : perms) {
                authorities.add(new SimpleGrantedAuthority(permCode));
            }
        }
        return authorities;
    }

    /**
     * 账号是否未过期（本平台不设账号有效期，恒为 true）
     *
     * @return true
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账号是否未锁定
     * <p>status=2 视为锁定；同时 lock_expire_time 到期后由业务侧自动改回 status=1。</p>
     *
     * @return true=未锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return status == null || status != UserStatusEnum.LOCKED.getCode();
    }

    /**
     * 凭证是否未过期（密码有效期策略由 password_version 控制，此处恒为 true）
     *
     * @return true
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账号是否启用
     *
     * @return true=status 为正常
     */
    @Override
    public boolean isEnabled() {
        return status != null && status == UserStatusEnum.NORMAL.getCode();
    }
}
