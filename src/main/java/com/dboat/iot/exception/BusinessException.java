package com.dboat.iot.exception;

import lombok.Getter;

/**
 * 业务异常类
 * <p>
 * 用于表示业务逻辑中的可预期错误（如设备不存在、参数非法等）。
 * 由 {@link com.dboat.iot.exception.GlobalExceptionHandler} 统一捕获处理，
 * 返回标准化的 {@link com.dboat.iot.dto.response.Result} 响应。
 * </p>
 *
 * @author dboat
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，默认 500 */
    private final int code;

    /**
     * 构造业务异常（默认错误码 500）
     *
     * @param message 错误描述信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 构造业务异常（自定义错误码）
     *
     * @param code    业务错误码
     * @param message 错误描述信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
