package com.dboat.user.common.constants;

/**
 * 认证鉴权模块 Redis Key 常量
 * <p>
 * 统一以 {@code auth:} 为顶层前缀，与 IoT 业务 Key（{@code iot:}、{@code ws:}）隔离。
 * 所有 Key 均以 {@code String.format} 模板形式定义，占位符含义在注释中说明。
 * </p>
 * <p>
 * Key 设计总览：
 * <ul>
 *   <li>Refresh Token → 会话映射（String，可撤销、可轮换）</li>
 *   <li>用户会话索引（ZSet，score=登录时间戳，用于并发会话控制与全量撤销）</li>
 *   <li>会话详情（String，记录 refreshToken / loginIp / userAgent 等）</li>
 *   <li>Access Token 黑名单（String，登出后在自然过期前主动失效）</li>
 *   <li>令牌版本 / 权限版本 / 角色集 / 权限集 缓存</li>
 *   <li>登录失败计数与锁定标记、验证码、接口限流计数</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
public final class AuthRedisKeys {

    private AuthRedisKeys() {
    }

    // ==================== 双令牌 / 会话 ====================

    /**
     * Refresh Token → 会话映射（String，value = RefreshSessionDTO JSON）
     * <p>占位符：%s = refreshToken</p>
     * <p>TTL = auth.jwt.refresh-token-ttl-seconds，登出/轮换/踢人时主动删除</p>
     */
    public static final String AUTH_REFRESH_TOKEN = "auth:refresh:token:%s";

    /**
     * 用户全部会话索引（ZSet，member = sessionId，score = 登录时间戳 ms）
     * <p>占位符：%s = userId</p>
     * <p>用途：并发会话数控制（超限踢最早）、一键撤销该用户全部会话</p>
     */
    public static final String AUTH_USER_SESSION_ZSET = "auth:user:sessions:%s";

    /**
     * 会话详情（String，value = UserSessionDTO JSON）
     * <p>占位符：%s = sessionId</p>
     */
    public static final String AUTH_SESSION_DETAIL = "auth:session:%s";

    /**
     * Access Token 黑名单（String，value = 撤销原因）
     * <p>占位符：%s = JWT 的 jti（sessionId）</p>
     * <p>TTL = 该 Access Token 的剩余有效期，自然过期后 Key 自动清理</p>
     */
    public static final String AUTH_ACCESS_BLACKLIST = "auth:access:blacklist:%s";

    // ==================== 版本 / 权限缓存 ====================

    /**
     * 用户令牌版本号缓存（String，对应 user.token_version）
     * <p>占位符：%s = userId</p>
     * <p>JWT 中的 tokenVersion 与此值不一致即判定令牌失效（踢人/封号/改密）</p>
     */
    public static final String AUTH_TOKEN_VERSION = "auth:user:tokenVersion:%s";

    /**
     * 用户权限版本号缓存（String，对应 user_auth.password_version）
     * <p>占位符：%s = userId</p>
     */
    public static final String AUTH_PERM_VERSION = "auth:user:permVersion:%s";

    /**
     * 用户角色编码集合缓存（String，value = JSON 数组，如 ["ADMIN"]）
     * <p>占位符：%s = userId</p>
     * <p>使用 String + JSON 而非 Set，便于用单次 GET 区分"未缓存"与"缓存为空集合"</p>
     */
    public static final String AUTH_ROLE_CACHE = "auth:user:roles:%s";

    /**
     * 用户权限编码集合缓存（String，value = JSON 数组，如 ["iot:device:read"]）
     * <p>占位符：%s = userId</p>
     */
    public static final String AUTH_PERM_CACHE = "auth:user:perms:%s";

    // ==================== 登录安全 ====================

    /**
     * 登录连续失败计数（String，INCR 计数）
     * <p>占位符：%s = orgId:identifier（未确定租户时退化为 identifier）</p>
     */
    public static final String AUTH_LOGIN_FAIL = "auth:login:fail:%s";

    /**
     * 账号锁定标记（String，value = 锁定截止时间戳 ms）
     * <p>占位符：%s = orgId:identifier</p>
     */
    public static final String AUTH_LOGIN_LOCK = "auth:login:lock:%s";

    /**
     * 认证接口限流计数（String，固定窗口 INCR + EXPIRE）
     * <p>占位符：%s = 请求URI，%s = 客户端IP</p>
     */
    public static final String AUTH_RATE_LIMIT = "auth:limit:%s:%s";

    // ==================== 验证码 ====================

    /**
     * 验证码（String，value = 验证码明文）
     * <p>占位符：%s = 业务场景 scene，%s = 目标标识（手机号/邮箱）</p>
     */
    public static final String AUTH_CAPTCHA = "auth:captcha:%s:%s";

    /**
     * 验证码发送间隔标记（String，防刷）
     * <p>占位符：%s = 业务场景 scene，%s = 目标标识（手机号/邮箱）</p>
     */
    public static final String AUTH_CAPTCHA_INTERVAL = "auth:captcha:interval:%s:%s";
}
