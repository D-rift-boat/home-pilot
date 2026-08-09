package com.dboat.iot.exception;

import com.dboat.iot.dto.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 使用 @RestControllerAdvice 统一拦截所有 Controller 抛出的异常，
 * 将异常转换为标准化的 {@link Result} 响应返回给前端。
 * 按异常类型分级处理：
 * <ul>
 *   <li>{@link BusinessException} - 业务异常，返回 OK + 错误信息</li>
 *   <li>{@link MethodArgumentNotValidException} - 参数校验异常（@Valid），返回 400</li>
 *   <li>{@link BindException} - 参数绑定异常，返回 400</li>
 *   <li>{@link Exception} - 未知系统异常，返回 500</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     * <p>业务可预期的错误，如设备不存在、参数不合法等</p>
     *
     * @param e       业务异常对象
     * @param request HTTP 请求对象（用于记录请求 URI）
     * @return 统一响应结果
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("Business exception at [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid 注解触发）
     * <p>提取所有字段校验错误信息，拼接后返回</p>
     *
     * @param e 校验异常对象
     * @return 统一响应结果（400）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Parameter validation failed");
        log.error("Validation exception: {}", message, e);
        return Result.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 处理参数绑定异常
     * <p>请求参数类型不匹配或格式错误时触发</p>
     *
     * @param e 绑定异常对象
     * @return 统一响应结果（400）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Parameter binding failed");
        log.error("Bind exception: {}", message, e);
        return Result.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 处理未知系统异常（兜底）
     * <p>捕获所有未被其他 Handler 处理的异常，返回 500 错误</p>
     *
     * @param e       异常对象
     * @param request HTTP 请求对象
     * @return 统一响应结果（500）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("Unexpected exception at [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error");
    }
}
