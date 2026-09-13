package com.dboat.user.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 认证用户加载服务
 * <p>
 * 在 Spring Security 标准 {@link UserDetailsService} 之上扩展多租户能力：
 * 登录标识可以是账号 / 手机号 / 邮箱（统一存于 {@code user_auth.identifier}），
 * 且同一标识可能存在于不同租户，因此需要支持显式传入 orgId 精确定位账号。
 * </p>
 *
 * @author dboat
 */
public interface AuthUserDetailsService extends UserDetailsService {

    /**
     * 按登录标识加载认证主体
     * <p>
     * 匹配顺序：先按 {@code user_auth.identifier}（手机号 / 邮箱 / 账号）匹配，
     * 未命中再按 {@code user.username} 匹配并回查其密码凭据，
     * 保证「用户名 + 密码」与「手机号 + 密码」两种登录方式都可用。
     * </p>
     *
     * @param orgId      租户ID，为空时跨租户匹配（命中多个将要求指定租户）
     * @param identifier 登录标识
     * @return 认证主体（含密码哈希、角色与权限集合）
     */
    LoginUser loadByIdentifier(String orgId, String identifier);

    /**
     * 按用户ID加载认证主体
     * <p>用于 JWT 过滤器复核账号实时状态（禁用 / 锁定 / 令牌版本变更）。</p>
     *
     * @param userId 用户ID
     * @return 认证主体，用户不存在时返回 null
     */
    LoginUser loadByUserId(String userId);

    /**
     * Spring Security 标准加载入口
     * <p>等价于 {@code loadByIdentifier(null, username)}，账号不存在时抛出
     * {@link org.springframework.security.core.userdetails.UsernameNotFoundException}。</p>
     *
     * @param username 登录标识
     * @return 认证主体
     */
    @Override
    UserDetails loadUserByUsername(String username);
}
