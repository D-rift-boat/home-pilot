package com.dboat.user.interceptor;

import com.dboat.user.security.AuthResponseWriter;
import com.dboat.user.service.ApiRateLimitService;
import com.dboat.user.utils.AuthWebUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证接口限流拦截器
 * <p>
 * 对登录、注册、刷新令牌、发送验证码等匿名接口做前置限流，
 * 抵御密码撞库与短信/邮件轰炸。限流维度为「请求 URI + 客户端真实 IP」，
 * 计数逻辑见 {@link ApiRateLimitService}。
 * </p>
 * <p>
 * 置于 Spring MVC 拦截器层而非安全过滤器层，是为了让限流只作用于认证入口，
 * 不影响已登录用户的高频业务接口（如实时看板轮询）。
 * </p>
 *
 * @author dboat
 */
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    /**
     * 需要限流的认证入口路径
     * <p>这些路径同时在 {@code auth.security.permit-all-paths} 中免认证，是匿名流量的唯一入口。</p>
     */
    public static final String[] LIMIT_PATHS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/captcha/send"
    };

    /** 限流服务 */
    @Resource
    private ApiRateLimitService apiRateLimitService;

    /**
     * 请求前置处理：限流校验
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  目标处理器
     * @return true=放行；false=已触发限流并写出 429 响应
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 跨域预检请求不消耗配额
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        String clientIp = AuthWebUtils.getClientIp(request);
        if (apiRateLimitService.tryAcquire(uri, clientIp)) {
            return true;
        }
        long retryAfter = apiRateLimitService.windowRemainSeconds(uri, clientIp);
        AuthResponseWriter.writeTooManyRequests(response, retryAfter);
        return false;
    }
}
