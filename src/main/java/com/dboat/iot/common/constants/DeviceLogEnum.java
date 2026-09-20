package com.dboat.iot.common.constants;

import lombok.Getter;

/**
 * 设备日志类型枚举
 */
@Getter
public enum DeviceLogEnum {

	/** 设备离线 */
	OFFLINE("00", "离线", "OFFLINE"),
	/** 设备上线 */
	ONLINE("01", "上线", "ONLINE"),
	/** 设备异常 */
	ABNORMAL("02", "异常", "ABNORMAL");

	private final String code;
	private final String msgCn;
	private final String msgEn;

	DeviceLogEnum(String code, String msgCn, String msgEn) {
		this.code = code;
		this.msgCn = msgCn;
		this.msgEn = msgEn;
	}

	/**
	 * 根据code反向查找枚举
	 */
	public static DeviceLogEnum getByCode(String code) {
		for (DeviceLogEnum value : DeviceLogEnum.values()) {
			if (value.getCode().equals(code)) {
				return value;
			}
		}
		return null;
	}
}

