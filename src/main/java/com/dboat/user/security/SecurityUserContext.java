package com.dboat.user.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文读取工具
 * <p>
 * IoT 业务层（设备查询、指令下发、分组管理）需要拿到当前用户的 {@code orgId} 做多租户数据隔离，
 * 以及 {@code userId} 做设备归属校验。此工具从 Spring Security 上下文中提取 {@link LoginUser}，
 * 使业务代码无需直接依赖 Security API。
 * </p>
 * <p>
 * <b>使用边界</b>：仅在 HTTP 请求线程中有效。MQTT / Kafka 消费线程、定时任务线程没有请求上下文，
 * 获取到的将是 null，此类链路必须显式传入 orgId（例如从消息体或设备记录中读取）。
 * 这也是本平台未启用 MyBatis-Plus 租户拦截器的原因——拦截器无法区分请求线程与后台线程。
 * </p>
 *
 * @author dboat
 */
public final class SecurityUserContext {

    private SecurityUserContext() {
    }

    /**
     * 获取当前登录用户主体
     *
     * @return 登录用户，未认证或处于后台线程时返回 null
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID，未认证时返回 null
     */
    public static String getUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

    /**
     * 获取当前登录用户所属租户ID
     * <p>IoT 业务查询必须携带此值做数据隔离。</p>
     *
     * @return 租户ID，未认证时返回 null
     */
    public static String getOrgId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getOrgId();
    }

    /**
     * 获取当前会话ID
     * <p>与 Access Token 的 jti 一致，登出与踢人时用于定位会话。</p>
     *
     * @return 会话ID，未认证时返回 null
     */
    public static String getSessionId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getSessionId();
    }

    /**
     * 判断当前用户是否已认证
     *
     * @return true=已认证
     */
    public static boolean isAuthenticated() {
        return getLoginUser() != null;
    }

    /**
     * 清空当前线程的安全上下文
     * <p>线程池复用场景下调用，避免上一个请求的用户信息泄漏到下一个任务。</p>
     */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
