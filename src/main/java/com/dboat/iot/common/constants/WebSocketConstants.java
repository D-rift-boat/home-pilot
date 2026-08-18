package com.dboat.iot.common.constants;

public class WebSocketConstants {
	/**
	 * Redis 路由 Key 前缀：ws:session:{userId}
	 */
	public static final String WS_ROUTER_PREFIX = "ws:session:";

	/**
	 * Redis 路由 Key TTL：60秒（前端30s心跳，留2倍余量）
	 */
	public static final long WS_ROUTER_TTL_SECONDS = 60;

	/**
	 * WebSocket 心跳超时时间：30秒
	 */
	public static final long WS_HEARTBEAT_TIMEOUT_MILLIS = 30_000;

}
