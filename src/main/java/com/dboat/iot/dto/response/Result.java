package com.dboat.iot.dto.response;

import lombok.Data;

/**
 * API 统一响应结果封装
 * <p>
 * 所有 API 接口统一返回此结构，前端根据 code 字段判断请求是否成功：
 * <ul>
 *   <li>code=200：请求成功，data 中包含业务数据</li>
 *   <li>code!=200：请求失败，message 中包含错误描述</li>
 * </ul>
 * 配合 {@link com.dboat.iot.exception.GlobalExceptionHandler} 统一异常处理使用。
 * </p>
 *
 * @param <T> 响应数据类型
 * @author dboat
 */
@Data
public class Result<T> {

    /** 响应状态码：200=成功，其他=失败 */
    private int code;

    /** 响应消息描述 */
    private String message;

    /** 响应数据（成功时包含业务数据，失败时为 null） */
    private T data;

    /** 私有构造方法，防止外部直接 new，使用静态工厂方法创建 */
    private Result() {}

    /**
     * 构建成功响应（携带数据）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应结果
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    /**
     * 构建成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return 成功响应结果
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构建失败响应（自定义状态码和消息）
     *
     * @param code    错误状态码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败响应结果
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }

    /**
     * 构建失败响应（默认 500 状态码）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败响应结果
     */
    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }
}
