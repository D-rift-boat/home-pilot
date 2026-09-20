package com.dboat.iot.common.constants;

public class MqttConstants {
	// ===================== 消息类型 Message Type =====================
	/**
	 * 设备上线
	 */
	public static final String ONLINE = "client.connected";
	/**
	 * 设备下线
	 */
	public static final String OFFLINE = "client.disconnected";
	/**
	 * 数据上报
	 */
	public static final String UP_DATA = "UP_DATA";
	/**
	 * iot心跳
	 */
	public static final String HEART_BEAT = "HEARTBEAT";


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
	 * 设备反向分组索引（Set，设备关联的所有groupId，group之间是树形关联的）
	 * iot:group:rel:{groupId}
	 * ex 24h，慢加载 带分布式锁避免并发数据库，用于设备上下线 通知关联用户
	 * 做所属设备用户的查询的一个索引，查devId的各group，然后查其各group用户权限hash 取出用户列表
	 * 对各用户做数量变化ws通知
	 *
	 */
	public static final String IOT_GROUP_REL = "iot:group:rel:{%s}";

	/**
	 * 分组用户权限（Hash）
	 * iot:{orgId}:group:auth:{groupId}
	 * hash 关联用户权限角色，field=userId，value=JSON权限信息
	 * groupId全局唯一可去掉org前缀；保留org方便按组织批量清理权限
	 */
	public static final String IOT_ORG_GROUP_AUTH = "iot:{%s}:group:auth:{%s}";

	/**
	 * 分组内在线设备（ZSet）
	 * iot:{orgId}:group:dev:mems:{groupId}
	 * zset 组在线设备，member=devId，score=ts
	 * 心跳、更新/新增 devId；巡检、上下线、删除；用于在线数量统计
	 *
	 * 设备上线：添加 devId
	 * 设备下线：不删除  保留，可以用于快速查询  最后心跳时间
	 * 设备心跳：更新/新增 devId
	 */
	public static final String IOT_ORG_GROUP_DEV_MEMS = "iot:{%s}:group:dev:mems:{%s}";

	/**
	 * 全局心跳分桶（ZSet，唯一时间基准，巡检用）
	 * iot:dev:global:bucket:{shardId}  shardId = hash(devId)%128
	 * zset 全局心跳分桶 128个桶，存 devId score = 心跳ts
	 * 心跳更新写入；巡检扫描超时设备；分批ZREM清理过期设备
	 * 遍历清理列表，执行ws通知、清理分组在线设备ZSet（清理时比对心跳时间）
	 *
	 * 设备上线：添加 devId
	 * 设备下线：删除 devId
	 * 设备心跳：更新/新增 devId
	 */
	public static final String IOT_DEV_GLOBAL_BUCKET = "iot:dev:global:bucket:{%s}";

	/**
	 * 设备分片总数
	 */
	public static final int DEV_SHARD_SIZE = 128;

	/**
	 * 设备心跳活跃key  iot:dev:active:{devId}  string json
	 */
	public static final String IOT_DEV_ACTIVE = "iot:dev:active:{%s}";



	//===============================  iot device expire time  ===============================
	/**
	 * iot device active expire time 24*60*60 s
	 */
	public static final int IOT_DEV_ACTIVE_EX = 24*60*60;


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
