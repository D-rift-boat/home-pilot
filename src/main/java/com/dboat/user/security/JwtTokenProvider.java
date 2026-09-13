package com.dboat.user.security;

import com.dboat.user.common.constants.AuthConstants;
import com.dboat.user.config.properties.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * JWT 令牌提供者（RS256 非对称签名）
 * <p>
 * 职责：
 * <ul>
 *   <li>启动时加载 RSA 密钥对（配置了 PEM 则加载，否则生成临时密钥对并告警）</li>
 *   <li>签发 Access Token：携带 userId(sub) / orgId / username / roleKeys / permVersion / tokenVersion / sessionId(jti)</li>
 *   <li>验签解析 Access Token，区分「已过期」与「签名非法」两类失败</li>
 *   <li>生成 Refresh Token：{@link SecureRandom} 强随机 32 字节转 16 进制，共 64 位字符串</li>
 * </ul>
 * </p>
 * <p>
 * 生产约束：必须配置固定的 {@code auth.jwt.private-key}，否则服务重启后临时密钥变更，
 * 会导致所有已签发的 Access Token 验签失败，多实例部署时更会出现跨节点验签不通。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** RSA 算法名称 */
    private static final String RSA_ALGORITHM = "RSA";

    /** PEM 头尾正则，用于剥离 -----BEGIN/END----- 标记 */
    private static final String PEM_HEADER_FOOTER_REGEX = "-----(BEGIN|END)[^-]+-----";

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /** RSA 私钥，仅用于签发 */
    @Getter
    private RSAPrivateKey privateKey;

    /** RSA 公钥，仅用于验签（可下发给网关独立验签） */
    @Getter
    private RSAPublicKey publicKey;

    /** 强随机数发生器，用于生成 Refresh Token */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 初始化 RSA 密钥对
     * <p>
     * 优先级：配置私钥 &gt; 生成临时密钥对。
     * 公钥未配置时从 PKCS#8 私钥（CRT 格式）中推导。
     * </p>
     */
    @PostConstruct
    public void initKeyPair() {
        AuthProperties.Jwt jwt = authProperties.getJwt();
        String pemPrivateKey = jwt.getPrivateKey();

        if (pemPrivateKey == null || pemPrivateKey.isBlank()) {
            // ===== 未配置密钥：生成临时密钥对，仅适用于单机开发环境 =====
            this.privateKey = generateTempPrivateKey(jwt.getRsaKeySize());
            this.publicKey = derivePublicKey(this.privateKey);
            log.warn("【JWT】未配置 auth.jwt.private-key，已生成临时 RSA 密钥对（{} bit）。" +
                    "服务重启后已签发令牌将全部失效，生产环境必须配置固定密钥！", jwt.getRsaKeySize());
            return;
        }

        // ===== 已配置密钥：加载 PEM =====
        this.privateKey = parsePrivateKey(pemPrivateKey);
        String pemPublicKey = jwt.getPublicKey();
        this.publicKey = (pemPublicKey == null || pemPublicKey.isBlank())
                ? derivePublicKey(this.privateKey)
                : parsePublicKey(pemPublicKey);
        log.info("【JWT】已加载 RSA 密钥对，签名算法 RS256，issuer={}", jwt.getIssuer());
    }

    /**
     * 签发 Access Token
     *
     * @param user      登录用户主体（含租户、角色、版本号）
     * @param sessionId 会话ID，写入 jti 声明，登出与踢人时据此定位
     * @return JWT 字符串
     */
    public String generateAccessToken(LoginUser user, String sessionId) {
        AuthProperties.Jwt jwt = authProperties.getJwt();
        long nowMs = System.currentTimeMillis();
        Date issuedAt = new Date(nowMs);
        Date expiration = new Date(nowMs + jwt.getAccessTokenTtlSeconds() * 1000L);

        // 角色集合作为数组声明写入，网关侧可直接读取做粗粒度鉴权
        List<String> roleKeys = new ArrayList<>(user.getRoleKeys() == null ? Set.of() : user.getRoleKeys());

        return Jwts.builder()
                .issuer(jwt.getIssuer())
                .subject(user.getUserId())
                .id(sessionId)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .claim(AuthConstants.CLAIM_ORG_ID, user.getOrgId())
                .claim(AuthConstants.CLAIM_USERNAME, user.getUsername())
                .claim(AuthConstants.CLAIM_NICKNAME, user.getNickname())
                .claim(AuthConstants.CLAIM_ROLE_KEYS, roleKeys)
                .claim(AuthConstants.CLAIM_PERM_VERSION, user.getPermVersion())
                .claim(AuthConstants.CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .claim(AuthConstants.CLAIM_SESSION_ID, sessionId)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 验签并解析 Access Token
     *
     * @param accessToken JWT 字符串
     * @return 令牌声明集合
     * @throws ExpiredJwtException 令牌已过期（业务侧应提示前端刷新令牌）
     * @throws JwtException        签名非法、格式错误、issuer 不匹配等
     */
    public Claims parseAccessToken(String accessToken) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(authProperties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
    }

    /**
     * 生成 Refresh Token（强随机 32 字节 → 64 位 16 进制字符串）
     *
     * @return Refresh Token
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[AuthConstants.REFRESH_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 生成会话ID（UUID 去横线）
     *
     * @return 会话ID
     */
    public String generateSessionId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Access Token 有效期（秒）
     *
     * @return 有效期秒数
     */
    public long getAccessTokenTtlSeconds() {
        return authProperties.getJwt().getAccessTokenTtlSeconds();
    }

    /**
     * Refresh Token 有效期（秒）
     *
     * @return 有效期秒数
     */
    public long getRefreshTokenTtlSeconds() {
        return authProperties.getJwt().getRefreshTokenTtlSeconds();
    }

    // ==================== 内部密钥处理 ====================

    /**
     * 解析 PKCS#8 格式 PEM 私钥
     *
     * @param pem 私钥 PEM 文本
     * @return RSA 私钥
     */
    private RSAPrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);
            return (RSAPrivateKey) KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalStateException("解析 auth.jwt.private-key 失败，请确认为 PKCS#8 PEM 格式", e);
        }
    }

    /**
     * 解析 X.509 格式 PEM 公钥
     *
     * @param pem 公钥 PEM 文本
     * @return RSA 公钥
     */
    private RSAPublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePem(pem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(der);
            return (RSAPublicKey) KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalStateException("解析 auth.jwt.public-key 失败，请确认为 X.509 PEM 格式", e);
        }
    }

    /**
     * 生成临时 RSA 私钥（开发环境兜底）
     *
     * @param keySize 密钥长度
     * @return RSA 私钥
     */
    private RSAPrivateKey generateTempPrivateKey(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(keySize, secureRandom);
            KeyPair keyPair = generator.generateKeyPair();
            return (RSAPrivateKey) keyPair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("生成临时 RSA 密钥对失败", e);
        }
    }

    /**
     * 从 CRT 私钥中推导公钥
     *
     * @param rsaPrivateKey RSA 私钥
     * @return RSA 公钥
     */
    private RSAPublicKey derivePublicKey(RSAPrivateKey rsaPrivateKey) {
        try {
            RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) rsaPrivateKey;
            RSAPublicKeySpec publicSpec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
            return (RSAPublicKey) KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(publicSpec);
        } catch (Exception e) {
            throw new IllegalStateException("从私钥推导公钥失败", e);
        }
    }

    /**
     * 剥离 PEM 头尾标记与全部空白字符后做 Base64 解码
     *
     * @param pem PEM 文本
     * @return DER 字节数组
     */
    private byte[] decodePem(String pem) {
        String body = pem.replaceAll(PEM_HEADER_FOOTER_REGEX, "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
