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

    @Schema(description = "查询起始UTC毫秒时间戳(世界时  0时区)", example = "1756000860000")
    private Long startTime;

    @Schema(description = "查询结束UTC毫秒时间戳(世界时  0时区)", example = "1756000880000")
    private Long endTime;
}
