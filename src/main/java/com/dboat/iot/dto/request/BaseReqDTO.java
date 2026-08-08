package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "Base request DTO")
public class BaseReqDTO implements Serializable {

    @Schema(description = "Request unique identifier, for tracing", example = "req-20250808-001")
    private String requestId;

    @Schema(description = "Request timestamp (ms)", example = "1754640000000")
    private Long timestamp;

    @Schema(description = "Request signature, for verification")
    private String sign;
}
