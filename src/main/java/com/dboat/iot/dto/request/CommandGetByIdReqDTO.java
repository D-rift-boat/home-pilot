package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 根据指令ID查询指令详情请求 DTO
 * <p>通过 POST /api/command/getById 接口提交</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "根据ID查询指令请求")
public class CommandGetByIdReqDTO extends BaseReqDTO {

    /** 指令内部主键ID（UUID） */
    @NotBlank(message = "Command ID cannot be empty")
    @Schema(description = "指令内部主键ID", example = "1234567890")
    private String id;
}
