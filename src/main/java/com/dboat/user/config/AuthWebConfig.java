package com.dboat.user.config;

import com.dboat.user.interceptor.AuthRateLimitInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 认证模块 Web MVC 配置
 * <p>
 * 独立于 {@code com.dboat.iot.config.WebConfig}，仅负责认证链路的拦截器注册，
 * 保持 IoT 与用户两个模块的配置边界清晰。
 * </p>
 *
 * @author dboat
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    /** 认证接口限流拦截器 */
    @Resource
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    /**
     * 注册拦截器
     * <p>限流仅作用于匿名认证入口，不影响已登录用户的业务接口吞吐。</p>
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns(AuthRateLimitInterceptor.LIMIT_PATHS)
                .order(0);
    }
}
