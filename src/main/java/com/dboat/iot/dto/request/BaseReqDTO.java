package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * API 请求基础 DTO —— 所有请求 DTO 的公共父类
 * <p>
 * 定义所有 API 请求的通用基础字段，用于请求追踪和签名校验。
 * 所有业务请求 DTO 必须继承此类，遵循项目统一 POST + DTO 规范。
 * </p>
 *
 * @author dboat
 */
@Data
@Schema(description = "Base request DTO - 所有请求DTO的公共基类")
public class BaseReqDTO implements Serializable {

    /**
     * 请求唯一标识，用于链路追踪和日志关联
     */
    @Schema(description = "请求唯一标识，用于链路追踪", example = "req-20250808-001")
    private String requestId;

    /**
     * 请求时间戳（毫秒），用于请求时效性校验
     */
    @Schema(description = "请求时间戳（毫秒）", example = "1754640000000")
    private Long timestamp;

    /**
     * 请求签名，用于接口安全校验（防篡改）
     */
    @Schema(description = "请求签名，用于安全校验")
    private String sign;
}
