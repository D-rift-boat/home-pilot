package com.dboat.user.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 无权限处理器（403）
 * <p>
 * 已通过认证但缺少目标资源所需角色 / 权限时触发，
 * 覆盖两类场景：SecurityConfig 的路径级鉴权与 {@code @PreAuthorize} 方法级鉴权。
 * 日志中输出用户与目标 URI，便于排查权限配置遗漏。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    /**
     * 处理无权限请求
     *
     * @param request               HTTP 请求
     * @param response              HTTP 响应
     * @param accessDeniedException 授权异常
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        LoginUser loginUser = SecurityUserContext.getLoginUser();
        log.warn("【越权拦截】{} {} userId={}, orgId={}, roleKeys={}, reason={}",
                request.getMethod(), request.getRequestURI(),
                loginUser == null ? "anonymous" : loginUser.getUserId(),
                loginUser == null ? "-" : loginUser.getOrgId(),
                loginUser == null ? "[]" : loginUser.getRoleKeys(),
                accessDeniedException.getMessage());
        AuthResponseWriter.writeForbidden(response);
    }
}
