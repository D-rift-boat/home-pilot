package com.dboat.user.common.constants;

import java.util.List;
import java.util.Map;

/**
 * 认证鉴权模块通用常量
 * <p>
 * 集中管理角色编码、JWT 自定义声明名、请求头名称、状态码等业务常量，
 * 避免魔法值散落在各层代码中。
 * </p>
 *
 * @author dboat
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    // ==================== HTTP 请求头 / 令牌前缀 ====================

    /** 认证请求头名称 */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 令牌前缀（含尾部空格） */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Refresh Token 请求头名称（也支持从请求体传递） */
    public static final String HEADER_REFRESH_TOKEN = "X-Refresh-Token";

    // ==================== JWT 自定义声明（Claim）名称 ====================

    /** 声明：租户ID */
    public static final String CLAIM_ORG_ID = "orgId";

    /** 声明：登录账号 */
    public static final String CLAIM_USERNAME = "username";

    /** 声明：昵称 */
    public static final String CLAIM_NICKNAME = "nickname";

    /** 声明：角色编码集合 */
    public static final String CLAIM_ROLE_KEYS = "roleKeys";

    /** 声明：权限版本号（权限集合缓存失效判定依据） */
    public static final String CLAIM_PERM_VERSION = "permVersion";

    /** 声明：令牌版本号（+1 后该用户所有已签发 token 全部失效） */
    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    /** 声明：会话ID，与 Redis 会话记录一一对应 */
    public static final String CLAIM_SESSION_ID = "sessionId";

    // ==================== 内置角色编码 ====================

    /** 租户管理员：拥有租户内全部权限 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 运维操作员：设备管理与指令下发权限 */
    public static final String ROLE_OPERATOR = "OPERATOR";

    /** 只读访客：仅查看权限，注册默认角色 */
    public static final String ROLE_VIEWER = "VIEWER";

    /** Spring Security 角色权限前缀 */
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    // ==================== IoT RBAC 权限编码 ====================

    /** 设备查看 */
    public static final String PERM_DEVICE_READ = "iot:device:read";

    /** 设备新增/修改/删除 */
    public static final String PERM_DEVICE_WRITE = "iot:device:write";

    /** 指令下发 */
    public static final String PERM_COMMAND_SEND = "iot:command:send";

    /** 传感器数据查看 */
    public static final String PERM_SENSOR_READ = "iot:sensor:read";

    /** 分组管理 */
    public static final String PERM_GROUP_MANAGE = "iot:group:manage";

    /** 用户管理 */
    public static final String PERM_USER_MANAGE = "iot:user:manage";

    /** 角色权限管理 */
    public static final String PERM_ROLE_MANAGE = "iot:role:manage";

    /** 实时看板查看 */
    public static final String PERM_DASHBOARD_READ = "iot:dashboard:read";

    /**
     * 内置角色的默认权限模板
     * <p>
     * 新租户创建时据此自动写入 {@code role_perm}，避免 OPERATOR / VIEWER 注册后无任何权限。
     * ADMIN 不在此列——其在 {@code UserAuthorityService} 中直接映射全量启用权限，
     * 无需逐条维护关联关系。
     * </p>
     */
    public static final Map<String, List<String>> BUILTIN_ROLE_PERM_TEMPLATE = Map.of(
            ROLE_OPERATOR, List.of(PERM_DASHBOARD_READ, PERM_DEVICE_READ, PERM_DEVICE_WRITE,
                    PERM_COMMAND_SEND, PERM_SENSOR_READ, PERM_GROUP_MANAGE),
            ROLE_VIEWER, List.of(PERM_DASHBOARD_READ, PERM_DEVICE_READ, PERM_SENSOR_READ)
    );

    // ==================== 默认租户信息 ====================

    /** 平台首个用户自动创建租户时的默认租户名称 */
    public static final String DEFAULT_ORG_NAME = "默认租户";

    /** 默认角色名称映射：ADMIN */
    public static final String ROLE_NAME_ADMIN = "租户管理员";

    /** 默认角色名称映射：OPERATOR */
    public static final String ROLE_NAME_OPERATOR = "运维操作员";

    /** 默认角色名称映射：VIEWER */
    public static final String ROLE_NAME_VIEWER = "只读访客";

    // ==================== 密码策略 ====================

    /**
     * 密码强度正则：≥8 位，且同时包含大写字母、小写字母、数字、特殊符号
     */
    public static final String PASSWORD_STRENGTH_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,64}$";

    /** 密码强度不合规提示语 */
    public static final String PASSWORD_STRENGTH_MSG =
            "密码长度至少8位，且必须同时包含大写字母、小写字母、数字和特殊符号";

    /** BCrypt 哈希成本因子（生产级下限为 10） */
    public static final int BCRYPT_STRENGTH = 10;

    // ==================== 手机号 / 邮箱校验 ====================

    /** 手机号正则（中国大陆 11 位） */
    public static final String MOBILE_REGEX = "^1[3-9]\\d{9}$";

    /** 邮箱正则 */
    public static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    // ==================== 会话 / 令牌 ====================

    /** 令牌类型：Bearer */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /** Refresh Token 字节长度（转 16 进制后为 64 位字符串） */
    public static final int REFRESH_TOKEN_BYTE_LENGTH = 32;
}
