package com.dboat.user.service;

import java.util.Set;

/**
 * 用户鉴权上下文服务（角色 / 权限 / 版本号，Redis 缓存优先 + DB 回源）
 * <p>
 * JWT 过滤器每次请求都需要拿到用户的角色与权限集合来构建 Spring Security 权限，
 * 若每次回源数据库将造成 3 张表关联查询的高频压力。此服务将结果缓存在 Redis：
 * <ul>
 *   <li>{@code auth:user:roles:{userId}} —— 角色编码集合（JSON 数组）</li>
 *   <li>{@code auth:user:perms:{userId}} —— 权限编码集合（JSON 数组）</li>
 *   <li>{@code auth:user:tokenVersion:{userId}} —— 令牌版本号</li>
 *   <li>{@code auth:user:permVersion:{userId}} —— 权限版本号（对应 user_auth.password_version）</li>
 * </ul>
 * 授权关系变更（改角色、改权限、改密、踢人）时由业务侧调用 evict 系列方法主动失效。
 * </p>
 *
 * @author dboat
 */
public interface UserAuthorityService {

    /**
     * 获取用户角色编码集合（缓存优先，未命中回源 DB 并回填）
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 角色编码集合，无角色时返回空集合
     */
    Set<String> getRoleKeys(String orgId, String userId);

    /**
     * 获取用户权限编码集合（缓存优先，未命中回源 DB 并回填）
     * <p>ADMIN 角色直接授予系统内全部启用权限，无需逐条维护 role_perm。</p>
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 权限编码集合，无权限时返回空集合
     */
    Set<String> getPermCodes(String orgId, String userId);

    /**
     * 获取用户令牌版本号（缓存优先，未命中回源 DB 并回填）
     *
     * @param userId 用户ID
     * @return 令牌版本号，用户不存在时返回 null
     */
    Integer getTokenVersion(String userId);

    /**
     * 获取用户权限版本号（缓存优先，未命中回源 DB 并回填）
     *
     * @param userId 用户ID
     * @return 权限版本号，无密码凭据时返回 0
     */
    Integer getPermVersion(String userId);

    /**
     * 写入令牌版本号与权限版本号缓存
     * <p>登录成功、刷新令牌时调用，避免后续请求回源数据库。</p>
     *
     * @param userId       用户ID
     * @param tokenVersion 令牌版本号
     * @param permVersion  权限版本号
     */
    void cacheVersions(String userId, Integer tokenVersion, Integer permVersion);

    /**
     * 失效用户的角色与权限缓存
     * <p>触发场景：角色授权变更、权限资源变更。</p>
     *
     * @param userId 用户ID
     */
    void evictAuthorities(String userId);

    /**
     * 失效用户全部鉴权缓存（角色、权限、令牌版本、权限版本）
     * <p>触发场景：修改密码、封号、踢人、Refresh Token 重放。</p>
     *
     * @param userId 用户ID
     */
    void evictAll(String userId);
}
