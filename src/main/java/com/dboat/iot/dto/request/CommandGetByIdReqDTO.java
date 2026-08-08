package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Command get by ID request")
public class CommandGetByIdReqDTO extends BaseReqDTO {

    @NotBlank(message = "Command ID cannot be empty")
    @Schema(description = "Command internal ID", example = "1234567890")
    private String id;
}
