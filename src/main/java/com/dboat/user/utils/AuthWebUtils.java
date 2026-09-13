package com.dboat.user.utils;

import com.dboat.user.common.constants.AuthConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 认证链路 Web 工具类
 * <p>
 * 集中处理客户端真实 IP 解析、User-Agent 提取、标识脱敏等与安全链路相关的通用逻辑，
 * 避免在过滤器、拦截器、Service 三层重复实现。
 * </p>
 *
 * @author dboat
 */
public final class AuthWebUtils {

    /** 常见反向代理透传真实 IP 的请求头，按优先级排列 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    /** 未知 IP 占位符 */
    private static final String UNKNOWN = "unknown";

    /** IPv4 本机回环地址 */
    private static final String LOCAL_IPV4 = "127.0.0.1";

    /** IPv6 本机回环地址 */
    private static final String LOCAL_IPV6 = "0:0:0:0:0:0:0:1";

    /** User-Agent 入库最大长度，与 auth_login_log.user_agent 字段长度对齐 */
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private AuthWebUtils() {
    }

    /**
     * 获取当前线程绑定的 HTTP 请求
     * <p>
     * 供 Service 层读取客户端 IP / UA 等上下文信息，使 Service 方法签名保持
     * 「仅接受 DTO」的项目规范，无需层层透传 {@code HttpServletRequest}。
     * </p>
     * <p>非 HTTP 线程（MQTT / Kafka / 定时任务）返回 null。</p>
     *
     * @return 当前请求，不存在时返回 null
     */
    public static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }

    /**
     * 获取当前请求的客户端 IP
     *
     * @return 客户端 IP，非 HTTP 线程返回 unknown
     */
    public static String currentClientIp() {
        return getClientIp(currentRequest());
    }

    /**
     * 获取当前请求的 User-Agent
     *
     * @return User-Agent，非 HTTP 线程返回 unknown
     */
    public static String currentUserAgent() {
        return getUserAgent(currentRequest());
    }

    /**
     * 解析客户端真实 IP
     * <p>
     * 依次尝试各代理透传头，X-Forwarded-For 可能包含多级代理链路（client, proxy1, proxy2），
     * 取第一段即最初的客户端地址。全部为空时退化为 {@code request.getRemoteAddr()}。
     * </p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP，无法解析时返回 unknown
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (isUsableIp(value)) {
                // 多级代理时第一个非 unknown 值即为真实客户端
                int commaIndex = value.indexOf(',');
                String ip = commaIndex > 0 ? value.substring(0, commaIndex) : value;
                return normalize(ip.trim());
            }
        }
        return normalize(request.getRemoteAddr());
    }

    /**
     * 提取客户端 User-Agent，超长自动截断，避免入库失败
     *
     * @param request HTTP 请求
     * @return User-Agent，缺失时返回 unknown
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN;
        }
        return userAgent.length() > USER_AGENT_MAX_LENGTH
                ? userAgent.substring(0, USER_AGENT_MAX_LENGTH)
                : userAgent;
    }

    /**
     * 判断登录标识是否为手机号
     *
     * @param identifier 登录标识
     * @return true=手机号格式
     */
    public static boolean isMobile(String identifier) {
        return StringUtils.hasText(identifier) && identifier.matches(AuthConstants.MOBILE_REGEX);
    }

    /**
     * 判断登录标识是否为邮箱
     *
     * @param identifier 登录标识
     * @return true=邮箱格式
     */
    public static boolean isEmail(String identifier) {
        return StringUtils.hasText(identifier) && identifier.matches(AuthConstants.EMAIL_REGEX);
    }

    /**
     * 登录标识脱敏，用于日志与响应回显
     * <p>手机号保留前 3 后 4；邮箱保留首字符与域名；其他保留前 2 后 2。</p>
     *
     * @param identifier 登录标识
     * @return 脱敏后的标识
     */
    public static String maskIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return "";
        }
        if (isMobile(identifier)) {
            return identifier.substring(0, 3) + "****" + identifier.substring(7);
        }
        int atIndex = identifier.indexOf('@');
        if (atIndex > 0) {
            String name = identifier.substring(0, atIndex);
            String prefix = name.length() > 1 ? name.substring(0, 1) : name;
            return prefix + "***" + identifier.substring(atIndex);
        }
        if (identifier.length() <= 4) {
            return identifier.charAt(0) + "***";
        }
        return identifier.substring(0, 2) + "***" + identifier.substring(identifier.length() - 2);
    }

    // ==================== 内部工具 ====================

    /**
     * 判断代理头取值是否可用
     *
     * @param value 请求头值
     * @return true=可用
     */
    private static boolean isUsableIp(String value) {
        return StringUtils.hasText(value) && !UNKNOWN.equalsIgnoreCase(value);
    }

    /**
     * 归一化 IP：IPv6 回环地址统一转为 IPv4 表示
     *
     * @param ip 原始 IP
     * @return 归一化后的 IP
     */
    private static String normalize(String ip) {
        if (!StringUtils.hasText(ip)) {
            return UNKNOWN;
        }
        return LOCAL_IPV6.equals(ip) ? LOCAL_IPV4 : ip;
    }
}
