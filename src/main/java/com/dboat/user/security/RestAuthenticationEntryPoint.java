package com.dboat.user.security;

import com.dboat.user.enums.AuthCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证处理器（401）
 * <p>
 * 拦截所有「未携带令牌 / 令牌无效却访问受保护资源」的请求，
 * 输出与业务接口一致的 {@code Result} 结构，避免前端拿到 Spring Security 默认的 HTML 登录页。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * 处理未认证请求
     *
     * @param request       HTTP 请求
     * @param response      HTTP 响应
     * @param authException 认证异常
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("【未认证拦截】{} {} reason={}", request.getMethod(), request.getRequestURI(),
                authException.getMessage());
        AuthResponseWriter.writeUnauthorized(response, AuthCodeEnum.UNAUTHORIZED);
    }
}
