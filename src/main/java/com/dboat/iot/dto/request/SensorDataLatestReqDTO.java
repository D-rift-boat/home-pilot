package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 传感器数据最新值查询请求 DTO
 * <p>通过 POST /api/sensorData/latest 接口提交，获取指定设备最新一条传感器上报数据</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "传感器最新数据查询请求")
public class SensorDataLatestReqDTO extends BaseReqDTO {

    /** 设备唯一标识 */
    @NotBlank(message = "Device ID cannot be empty")
    @Schema(description = "设备标识", example = "esp32s3_001")
    private String deviceId;
}
