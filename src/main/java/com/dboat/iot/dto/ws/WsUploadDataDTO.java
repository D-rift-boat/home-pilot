package com.dboat.iot.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * WS推送的实时数据（后端→前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsUploadDataDTO {

	/**
	 * 消息类型，固定为 REAL_TIME_DATA
	 */
	private String type;

	/**
	 * 实时传感器数据
	 */
	private DataDTO data = new DataDTO();

	/**
	 * 设备信息
	 */
	private DeviceDTO device = new DeviceDTO();

	/**
	 * 时间戳（毫秒）
	 */
	private String timestamp;

	// ==================== 内部类 ====================

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DataDTO {
		/**
		 * AHT20温度（℃）
		 */
		private String tempAht;
		/**
		 * BMP280温度（℃）
		 */
		private String tempBmp;
		/**
		 * 湿度（%）
		 */
		private String humidity;
		/**
		 * 气压（hPa）
		 */
		private String pressureHpa;
		/**
		 * 海拔（m）
		 */
		private String altitude;
		/**
		 * IoT设备在线数量
		 */
		private Integer iotDeviceOnlineCount;
		/**
		 * 用户前端设备在线数量
		 */
		private Integer userDeviceOnlineCount;
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DeviceDTO {
		/**
		 * 设备ID
		 */
		private String deviceId;
		/**
		 * 设备状态
		 */
		private Integer deviceStatus;
		/**
		 * AHT20传感器状态
		 */
		private Integer aht20Status;
		/**
		 * BMP280传感器状态
		 */
		private Integer bmp280Status;
	}

	// ==================== 便捷方法 ====================

	/**
	 * 创建消息实例，自动填充type和timestamp
	 */
	public static WsUploadDataDTO of(DataDTO data, DeviceDTO device) {
		return WsUploadDataDTO.builder()
				.type("REAL_TIME_DATA")
				.data(data)
				.device(device)
				.timestamp(String.valueOf(System.currentTimeMillis()))
				.build();
	}
}
