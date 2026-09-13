package com.dboat.user.service.impl;

import com.alibaba.fastjson2.JSON;
import com.dboat.user.common.constants.AuthConstants;
import com.dboat.user.config.properties.AuthProperties;
import com.dboat.user.entity.User;
import com.dboat.user.entity.UserAuth;
import com.dboat.user.service.PermissionService;
import com.dboat.user.service.RoleService;
import com.dboat.user.service.UserAuthService;
import com.dboat.user.service.UserAuthorityService;
import com.dboat.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_PERM_CACHE;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_PERM_VERSION;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_ROLE_CACHE;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_TOKEN_VERSION;

/**
 * 用户鉴权上下文服务实现
 * <p>
 * 缓存策略：读多写少，采用「Cache-Aside + 主动失效」。
 * 所有缓存均带 TTL 兜底，即使业务侧漏调 evict 也不会长期脏读。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Service
public class UserAuthorityServiceImpl implements UserAuthorityService {

    /** Redis 操作模板（纯字符串序列化，便于 redis-cli 排查） */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /** 角色服务，缓存未命中时回源 */
    @Resource
    private RoleService roleService;

    /** 权限服务，缓存未命中时回源 */
    @Resource
    private PermissionService permissionService;

    /** 用户服务，读取 token_version */
    @Resource
    private UserService userService;

    /** 用户认证凭据服务，读取 password_version */
    @Resource
    private UserAuthService userAuthService;

    /**
     * 获取用户角色编码集合（缓存优先，未命中回源 DB 并回填）
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 角色编码集合
     */
    @Override
    public Set<String> getRoleKeys(String orgId, String userId) {
        String cacheKey = String.format(AUTH_ROLE_CACHE, userId);
        Set<String> cached = readJsonSet(cacheKey);
        if (cached != null) {
            return cached;
        }
        // 回源数据库并回填缓存
        Set<String> roleKeys = new LinkedHashSet<>(roleService.listRoleCodesByUserId(orgId, userId));
        writeJsonSet(cacheKey, roleKeys, authProperties.getCache().getPermTtlSeconds());
        return roleKeys;
    }

    /**
     * 获取用户权限编码集合（缓存优先，未命中回源 DB 并回填）
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @return 权限编码集合
     */
    @Override
    public Set<String> getPermCodes(String orgId, String userId) {
        String cacheKey = String.format(AUTH_PERM_CACHE, userId);
        Set<String> cached = readJsonSet(cacheKey);
        if (cached != null) {
            return cached;
        }

        // ADMIN 直接授予全量权限，避免逐条维护 role_perm 关联
        Set<String> roleKeys = getRoleKeys(orgId, userId);
        Set<String> permCodes = roleKeys.contains(AuthConstants.ROLE_ADMIN)
                ? new LinkedHashSet<>(permissionService.listAllEnabledPermCodes())
                : new LinkedHashSet<>(permissionService.listPermCodesByUserId(orgId, userId));

        writeJsonSet(cacheKey, permCodes, authProperties.getCache().getPermTtlSeconds());
        return permCodes;
    }

    /**
     * 获取用户令牌版本号（缓存优先，未命中回源 DB 并回填）
     *
     * @param userId 用户ID
     * @return 令牌版本号，用户不存在时返回 null
     */
    @Override
    public Integer getTokenVersion(String userId) {
        String cacheKey = String.format(AUTH_TOKEN_VERSION, userId);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return Integer.valueOf(cached);
        }
        User user = userService.getUserById(userId);
        if (user == null) {
            return null;
        }
        Integer tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        stringRedisTemplate.opsForValue().set(cacheKey, String.valueOf(tokenVersion),
                authProperties.getCache().getTokenVersionTtlSeconds(), TimeUnit.SECONDS);
        return tokenVersion;
    }

    /**
     * 获取用户权限版本号（缓存优先，未命中回源 DB 并回填）
     *
     * @param userId 用户ID
     * @return 权限版本号
     */
    @Override
    public Integer getPermVersion(String userId) {
        String cacheKey = String.format(AUTH_PERM_VERSION, userId);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return Integer.valueOf(cached);
        }
        UserAuth userAuth = userAuthService.getPasswordAuthByUserId(userId);
        Integer permVersion = (userAuth == null || userAuth.getPasswordVersion() == null)
                ? 0 : userAuth.getPasswordVersion();
        stringRedisTemplate.opsForValue().set(cacheKey, String.valueOf(permVersion),
                authProperties.getCache().getTokenVersionTtlSeconds(), TimeUnit.SECONDS);
        return permVersion;
    }

    /**
     * 写入令牌版本号与权限版本号缓存
     *
     * @param userId       用户ID
     * @param tokenVersion 令牌版本号
     * @param permVersion  权限版本号
     */
    @Override
    public void cacheVersions(String userId, Integer tokenVersion, Integer permVersion) {
        long ttl = authProperties.getCache().getTokenVersionTtlSeconds();
        stringRedisTemplate.opsForValue().set(String.format(AUTH_TOKEN_VERSION, userId),
                String.valueOf(tokenVersion == null ? 0 : tokenVersion), ttl, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(String.format(AUTH_PERM_VERSION, userId),
                String.valueOf(permVersion == null ? 0 : permVersion), ttl, TimeUnit.SECONDS);
    }

    /**
     * 失效用户的角色与权限缓存
     *
     * @param userId 用户ID
     */
    @Override
    public void evictAuthorities(String userId) {
        stringRedisTemplate.delete(List.of(
                String.format(AUTH_ROLE_CACHE, userId),
                String.format(AUTH_PERM_CACHE, userId)));
        log.debug("【鉴权缓存】已失效用户角色权限缓存 userId={}", userId);
    }

    /**
     * 失效用户全部鉴权缓存
     *
     * @param userId 用户ID
     */
    @Override
    public void evictAll(String userId) {
        stringRedisTemplate.delete(List.of(
                String.format(AUTH_ROLE_CACHE, userId),
                String.format(AUTH_PERM_CACHE, userId),
                String.format(AUTH_TOKEN_VERSION, userId),
                String.format(AUTH_PERM_VERSION, userId)));
        log.debug("【鉴权缓存】已失效用户全部鉴权缓存 userId={}", userId);
    }

    // ==================== 内部工具 ====================

    /**
     * 读取 JSON 数组缓存并转为集合
     *
     * @param cacheKey 缓存 Key
     * @return 集合；缓存不存在时返回 null（用于区分"未缓存"与"缓存为空集合"）
     */
    private Set<String> readJsonSet(String cacheKey) {
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached == null) {
            return null;
        }
        try {
            return new LinkedHashSet<>(JSON.parseArray(cached, String.class));
        } catch (Exception e) {
            // 缓存内容损坏时删除并回源，避免持续报错
            log.warn("【鉴权缓存】缓存内容解析失败，已删除并回源 key={}", cacheKey);
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    /**
     * 将集合序列化为 JSON 数组写入缓存
     *
     * @param cacheKey   缓存 Key
     * @param values     集合内容
     * @param ttlSeconds 过期时间（秒）
     */
    private void writeJsonSet(String cacheKey, Set<String> values, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(values), ttlSeconds, TimeUnit.SECONDS);
    }
}
