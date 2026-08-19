package com.dboat.iot.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 各传感器独立状态
 *
 * @author dboat
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IotDevLineDTO {
	/**
	 * 设备id
	 */
	private String deviceId;
	/**
	 * 最后报告时间
	 */
	private String lastReportTs;
}
