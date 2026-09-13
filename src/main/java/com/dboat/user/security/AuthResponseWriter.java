package com.dboat.user.security;

import com.dboat.iot.dto.response.Result;
import com.dboat.iot.utils.JsonUtils;
import com.dboat.user.enums.AuthCodeEnum;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证链路统一响应写出工具
 * <p>
 * 过滤器、401/403 处理器、限流拦截器都发生在 Spring MVC 之前，
 * 无法借助 {@code @RestControllerAdvice} 与消息转换器，必须手动写出 JSON。
 * 统一由本工具输出 {@link Result} 结构，保证与业务接口的响应契约完全一致。
 * </p>
 *
 * @author dboat
 */
@Slf4j
public final class AuthResponseWriter {

    private AuthResponseWriter() {
    }

    /**
     * 写出认证错误响应
     * <p>HTTP 状态码固定 401，业务错误码由 {@link AuthCodeEnum} 细化，
     * 前端据此区分「令牌过期（可静默刷新）」与「令牌失效（必须重新登录）」。</p>
     *
     * @param response  HTTP 响应
     * @param codeEnum  认证错误码
     * @throws IOException 写出失败
     */
    public static void writeUnauthorized(HttpServletResponse response, AuthCodeEnum codeEnum) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, codeEnum.getCode(), codeEnum.getNameCn());
    }

    /**
     * 写出无权限响应（HTTP 403）
     *
     * @param response HTTP 响应
     * @throws IOException 写出失败
     */
    public static void writeForbidden(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN, AuthCodeEnum.FORBIDDEN.getCode(), AuthCodeEnum.FORBIDDEN.getNameCn());
    }

    /**
     * 写出限流响应（HTTP 429）
     *
     * @param response     HTTP 响应
     * @param retryAfterSec 建议重试等待秒数
     * @throws IOException 写出失败
     */
    public static void writeTooManyRequests(HttpServletResponse response, long retryAfterSec) throws IOException {
        if (retryAfterSec > 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSec));
        }
        write(response, HttpStatus.TOO_MANY_REQUESTS,
                AuthCodeEnum.RATE_LIMITED.getCode(), AuthCodeEnum.RATE_LIMITED.getNameCn());
    }

    /**
     * 按指定 HTTP 状态码与业务码写出 {@link Result} JSON
     *
     * @param response   HTTP 响应
     * @param httpStatus HTTP 状态码
     * @param code       业务错误码
     * @param message    错误提示
     * @throws IOException 写出失败
     */
    public static void write(HttpServletResponse response, HttpStatus httpStatus, int code, String message)
            throws IOException {
        if (response.isCommitted()) {
            log.warn("【认证响应】响应已提交，无法写出错误信息 code={}, message={}", code, message);
            return;
        }
        response.reset();
        response.setStatus(httpStatus.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJSONString(Result.fail(code, message)));
        response.getWriter().flush();
    }
}
