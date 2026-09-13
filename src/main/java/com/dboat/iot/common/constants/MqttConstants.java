package com.dboat.iot.common.constants;

public class MqttConstants {
	// ===================== 消息类型 Message Type =====================
	/**
	 * 设备上线
	 */
	public static final String ONLINE = "ONLINE";
	/**
	 * 设备下线
	 */
	public static final String OFFLINE = "OFFLINE";
	/**
	 * 数据上报
	 */
	public static final String UP_DATA = "UP_DATA";
	/**
	 * iot心跳
	 */
	public static final String HEART_BEAT = "HEART_BEAT";


	// ===================== 分组 Group 维度 Key =====================
	/**
	 * 分组元数据 Hash
	 * key: iot:group:meta:{g#%s}
	 * args[0]: groupId
	 */
	public static final String IOT_GROUP_META = "iot:group:meta:{g#%s}";

	/**
	 * 分组静态设备成员 Set，保存本组全部直属devId（静态归属，心跳不修改）
	 * key: iot:group:members:{g#%s}
	 * args[0]: groupId
	 */
	public static final String IOT_GROUP_MEMBERS = "iot:group:members:{g#%s}";

	/**
	 * 分组在线设备ZSet，member=devId，score=lastActiveTs(ms)，仅本组直属设备，不含子组
	 * key: iot:group:dev:mems:{g#%s}
	 * args[0]: groupId
	 */
	public static final String IOT_GROUP_DEV_MEMS = "iot:group:dev:mems:{g#%s}";

	// ===================== 设备 Device 维度 Key =====================
	/**
	 * iot设备影子  iot:dev:shadow:{devId}
	 * ex 24h
	 * 低频设备心跳主题 更新
	 * 设备上线 新建
	 * 设备下线 删除
	 */
	public static final String IOT_DEV_SHADOW = "iot:dev:shadow:{%s}";

	/**
	 * 用户分组  iot:user:group:{uid}
	 */
	public static final String IOT_USER_GROUP = "iot:user:group:{%s}";

	/**
	 * 设备心跳活跃key  iot:dev:active:{devId}
	 */
	public static final String IOT_DEV_ACTIVE = "iot:dev:active:{%s}";



	//===============================  iot device expire time  ===============================
	/**
	 * iot device active expire time 90s
	 */
	public static final int IOT_DEV_ACTIVE_EX = 90;


	/**
	 * iot设备-订阅者列表
	 */
	public static final String IOT_DEVICE_SUB_PREFIX = "iot:device:sub:";
	/**
	 * 用户-订阅设备列表
	 */
	public static final String USER_IOT_DEVICE_SUB_PREFIX = "iot:user:sub:";

	/**
	 * IoT 设备业务离线判定阈值（毫秒）：60秒无上报 → 判定离线
	 */
	public static final long IOT_DEVICE_OFFLINE_TIMEOUT_MS = 60_000;
}
