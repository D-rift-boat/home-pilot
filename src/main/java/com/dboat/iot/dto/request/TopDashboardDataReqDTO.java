package com.dboat.iot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 仪表盘统计查询请求 DTO
 * <p>
 * 用于实时监控面板查询统计数据，可指定设备ID获取特定设备的实时数据；
 * 若不传 deviceId，则默认取第一个在线设备的数据作为面板展示。
 * </p>
 *
 * @author dboat
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "仪表盘统计查询请求")
public class TopDashboardDataReqDTO extends BaseReqDTO {

    /** 用户唯一标识（可选，不传则取第一个在线用户） */
    @Schema(description = "用户标识（可选）")
    private String userId;
}
