package com.dboat.user.exception;

import com.dboat.iot.exception.BusinessException;
import com.dboat.user.enums.AuthCodeEnum;

/**
 * 认证鉴权业务异常
 * <p>
 * 继承项目统一的 {@link BusinessException}，因此可被
 * {@link com.dboat.iot.exception.GlobalExceptionHandler} 直接捕获并转换为标准 {@code Result} 响应，
 * 无需额外注册异常处理器。
 * </p>
 *
 * @author dboat
 */
public class AuthException extends BusinessException {

    /**
     * 使用认证错误码枚举构造异常
     *
     * @param codeEnum 认证错误码枚举（携带 code 与对外提示语）
     */
    public AuthException(AuthCodeEnum codeEnum) {
        super(codeEnum.getCode(), codeEnum.getNameCn());
    }

    /**
     * 使用认证错误码枚举 + 自定义提示语构造异常
     *
     * @param codeEnum 认证错误码枚举
     * @param message  自定义对外提示语
     */
    public AuthException(AuthCodeEnum codeEnum, String message) {
        super(codeEnum.getCode(), message);
    }
}
