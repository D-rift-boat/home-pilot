package com.dboat.iot.ws;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Data
@Builder
public class WsSession {
	/**
	 * session
	 */
	private WebSocketSession webSocketSession;

	/**
	 * 用户id
	 */
	private String userId;

	/**
	 * 最近心跳时间
	 */
	private long lastHeartbeatTime;
}
