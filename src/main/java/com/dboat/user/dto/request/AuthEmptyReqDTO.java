package com.dboat.user.dto.request;

import com.dboat.iot.dto.request.BaseReqDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 认证接口通用空请求 DTO
 * <p>
 * 用于登出、查询当前登录用户等无业务入参的接口，
 * 保持项目"统一 POST + DTO 封装"的接口规范，同时继承 {@link BaseReqDTO} 的链路追踪字段。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "认证接口通用空请求体")
public class AuthEmptyReqDTO extends BaseReqDTO {
}
