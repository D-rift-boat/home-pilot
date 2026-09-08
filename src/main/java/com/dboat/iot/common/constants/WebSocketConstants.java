package com.dboat.iot.common.constants;

public class WebSocketConstants {
	/**
	 * 心跳ping
	 */
	public static final String PING = "PING";

	/**
	 * 心跳pong
	 */
	public static final String PONG = "PONG";

	/**
	 * Redis 路由 Key 前缀：ws:session:{userId} hash
	 */
	public static final String WS_USER_SESSION_PREFIX = "ws:user:ses:{%s}";

	/**
	 * Redis 路由 Key 前缀：ws:on:users	ZSET
	 */
	public static final String WS_ONLINE_USERS_KEY = "ws:on:users";

	/**
	 * Redis 路由 Key 前缀：ws:on:user:ses:{Uid}	ZSET  用户在线session 索引
	 */
	public static final String WS_USER_ONLINE_SESSION_PREFIX = "ws:on:user:ses:{%s}";

	/**
	 * Redis ws中继 Key 前缀：ws:relay:
	 */
	public static final String WS_RELAY_PREFIX = "ws:relay:";

	/**
	 * Redis 路由 Key TTL：60秒（前端15s心跳，留2倍余量）
	 */
	public static final long WS_ROUTER_TTL_SECONDS = 45;

	/**
	 * WebSocket 心跳超时时间：60秒，心跳间隔是30秒  超时设为60s 必须大于心跳间隔时间
	 * 不然定时任务巡检删除本地会话 可能会误判（60s时先巡检的话 可能会删除掉本地会话）
	 * 实际最大关闭延迟	60+15=75s	可接受
	 */
	public static final long WS_HEARTBEAT_TIMEOUT_MILLIS = 60_000;

}
