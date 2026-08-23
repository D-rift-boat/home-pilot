package com.dboat.iot.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 传感器历史数据范围查询请求 DTO
 * <p>
 * 通过 POST /api/sensorData/history 接口提交，按时间范围查询指定设备的传感器遥测数据。
 * 时间格式：yyyy-MM-dd HH:mm:ss，时区为 GMT+8。
 * 如不传起止时间，默认查询最近 1 小时的数据。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "传感器历史数据查询请求")
public class SensorDataQueryReqDTO extends BaseReqDTO {

    /** 设备唯一标识 */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "设备标识", example = "esp32-S3-001")
    private String deviceId;

    /** 查询起始时间（含），格式 yyyy-MM-dd HH:mm:ss，不传则默认 1 小时前 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "起始时间", example = "2025-01-01 00:00:00")
    private LocalDateTime startTime;

    /** 查询结束时间（含），格式 yyyy-MM-dd HH:mm:ss，不传则默认当前时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "结束时间", example = "2025-12-31 23:59:59")
    private LocalDateTime endTime;
}
