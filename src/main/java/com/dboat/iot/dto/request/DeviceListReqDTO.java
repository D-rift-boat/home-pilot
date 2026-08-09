package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备分页列表查询请求 DTO
 * <p>通过 POST /api/device/list 接口提交，支持按设备ID、名称、型号模糊/精确查询</p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备分页列表查询请求")
public class DeviceListReqDTO extends BaseReqDTO {

    /** 设备ID模糊匹配条件 */
    @Schema(description = "设备标识（模糊匹配）", example = "esp32")
    private String deviceId;

    /** 设备名称模糊匹配条件 */
    @Schema(description = "设备名称（模糊匹配）", example = "Living Room")
    private String deviceName;

    /** 设备型号精确匹配条件 */
    @Schema(description = "设备型号（精确匹配）", example = "ESP32-S3")
    private String deviceModel;

    /** 当前页码（从 1 开始），默认第 1 页 */
    @Schema(description = "页码", example = "1", defaultValue = "1")
    private Integer pageNum = 1;

    /** 每页记录数，默认 10 条 */
    @Schema(description = "每页条数", example = "10", defaultValue = "10")
    private Integer pageSize = 10;
}
