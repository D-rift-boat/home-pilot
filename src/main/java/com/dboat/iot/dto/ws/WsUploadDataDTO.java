package com.dboat.iot.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WS推送的实时数据（后端→前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsUploadDataDTO {

	/** 消息类型，固定为 REAL_TIME_DATA */
	private String type;

	/** 实时传感器数据 */
	private DataDTO data;

	/** 设备信息 */
	private DeviceDTO device;

	/** 时间戳（毫秒） */
	private Long timestamp;

	// ==================== 内部类 ====================

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DataDTO {
		/** AHT20温度（℃） */
		private Double tempAht;
		/** 湿度（%） */
		private Double humidity;
		/** 气压（hPa） */
		private Double pressureHpa;
		/** 海拔（m） */
		private Double altitude;
		/** IoT设备在线数量 */
		private Long iotDeviceOnlineCount;
		/** 用户前端设备在线数量 */
		private Long userDeviceOnlineCount;
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DeviceDTO {
		/** 设备ID */
		private String deviceId;
		/** 设备状态 */
		private Long deviceStatus;
		/** AHT20传感器状态 */
		private Long aht20Status;
		/** BMP280传感器状态 */
		private Long bmp280Status;
		/** BMP280温度（℃） */
		private Double tempBmp;
	}

	// ==================== 便捷方法 ====================

	/** 创建消息实例，自动填充type和timestamp */
	public static WsUploadDataDTO of(DataDTO data, DeviceDTO device) {
		return WsUploadDataDTO.builder()
				.type("REAL_TIME_DATA")
				.data(data)
				.device(device)
				.timestamp(System.currentTimeMillis())
				.build();
	}
}
