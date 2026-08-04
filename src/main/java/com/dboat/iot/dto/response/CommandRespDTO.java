package com.dboat.iot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Device command response")
public class CommandRespDTO {

    @Schema(description = "Command ID")
    private String id;

    @Schema(description = "Target device ID")
    private String deviceId;

    @Schema(description = "Command content")
    private String command;

    @Schema(description = "Command status: 0=pending, 1=sent, 2=success, 3=failed")
    private Integer status;

    @Schema(description = "Created time")
    private LocalDateTime createTime;

    @Schema(description = "Updated time")
    private LocalDateTime updateTime;
}
