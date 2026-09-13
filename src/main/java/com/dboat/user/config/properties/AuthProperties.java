package com.dboat.user.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证鉴权体系配置属性
 * <p>
 * 对应 application.yml 中的 {@code auth.*} 配置段，集中管理双令牌时效、
 * 登录安全策略、验证码策略、限流策略、权限缓存策略与免认证白名单。
 * </p>
 *
 * @author dboat
 */
@Component
@ConfigurationProperties(prefix = "auth")
@Data
public class AuthProperties {

    /** JWT 相关配置 */
    private Jwt jwt = new Jwt();

    /** 登录安全策略配置 */
    private Login login = new Login();

    /** 验证码配置 */
    private Captcha captcha = new Captcha();

    /** 认证接口限流配置 */
    private RateLimit rateLimit = new RateLimit();

    /** 权限缓存配置 */
    private Cache cache = new Cache();

    /** 安全路径配置 */
    private Security security = new Security();

    /**
     * JWT 双令牌配置
     * <p>Access Token 使用 RS256 非对称签名，私钥签发、公钥验签，支持网关侧独立验签。</p>
     */
    @Data
    public static class Jwt {

        /** 签发方标识（iss），验签时强制校验 */
        private String issuer = "home-pilot";

        /** Access Token 有效期（秒），默认 2 小时 */
        private long accessTokenTtlSeconds = 7200;

        /** Refresh Token 有效期（秒），默认 7 天 */
        private long refreshTokenTtlSeconds = 604800;

        /** RSA 密钥长度（未配置密钥时生成临时密钥对使用） */
        private int rsaKeySize = 2048;

        /** RSA 私钥（PKCS#8 PEM 格式，支持多行），为空则启动时生成临时密钥对 */
        private String privateKey;

        /** RSA 公钥（X.509 PEM 格式，支持多行），为空则从私钥推导 */
        private String publicKey;
    }

    /**
     * 登录安全策略配置
     */
    @Data
    public static class Login {

        /** 连续密码错误次数阈值，达到后锁定账号 */
        private int maxFailCount = 5;

        /** 账号锁定时长（分钟） */
        private int lockMinutes = 10;

        /** 单用户最大并发会话数，超限踢掉最早登录的会话 */
        private int maxSessions = 5;

        /** 首次登录是否强制改密 */
        private boolean forceChangePasswordOnFirstLogin = true;
    }

    /**
     * 验证码配置
     */
    @Data
    public static class Captcha {

        /** 验证码长度 */
        private int length = 6;

        /** 验证码有效期（秒） */
        private long ttlSeconds = 300;

        /** 同一目标发送间隔（秒），防刷 */
        private long sendIntervalSeconds = 60;

        /** 模拟发送开关：true 时验证码写入日志并在响应中返回（仅限开发环境） */
        private boolean mockEnabled = true;
    }

    /**
     * 认证接口限流配置（基于 Redis + Lua 固定窗口计数）
     */
    @Data
    public static class RateLimit {

        /** 限流开关 */
        private boolean enabled = true;

        /** 统计窗口（秒） */
        private long windowSeconds = 60;

        /** 单 IP 在窗口内允许的最大请求数 */
        private int maxRequests = 30;
    }

    /**
     * 权限缓存配置
     */
    @Data
    public static class Cache {

        /** 角色/权限集合缓存有效期（秒） */
        private long permTtlSeconds = 1800;

        /** 令牌版本号缓存有效期（秒） */
        private long tokenVersionTtlSeconds = 300;
    }

    /**
     * 安全路径配置
     */
    @Data
    public static class Security {

        /** 免认证白名单路径（Ant 风格） */
        private List<String> permitAllPaths = new ArrayList<>();
    }
}
