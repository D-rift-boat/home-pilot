package com.dboat.iot.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 本地 WebSocket 会话管理器
 * <p>
 * 统一管理本节点所有 WS 会话的内存状态，包括：
 * <ul>
 *   <li>sessionMap：sessionId → WsSession（内存会话表）</li>
 *   <li>sessionRedisKeyMap：sessionId → Redis 路由 Key（用于关闭时清理）</li>
 *   <li>心跳刷新、按用户查询、广播推送等</li>
 * </ul>
 * 原散落在 DeviceWebSocketHandler 中的本地缓存逻辑统一收归此类管理。
 * </p>
 *
 * @author dboat
 */
@Component
@Slf4j
public class LocalWsSessionManager {

	/**
	 * 内存会话表：key = sessionId, value = WsSession（封装了 userId 的 session）
	 */
	private final ConcurrentHashMap<String, WsSession> sessionMap = new ConcurrentHashMap<>();

	/**
	 * Session 与 Redis 路由 Key 的映射：sessionId → redisKey，用于关闭时清理
	 */
	private final ConcurrentHashMap<String, String> sessionRedisKeyMap = new ConcurrentHashMap<>();

	// ==================== 会话生命周期 ====================

	/**
	 * WS 连接建立时注册会话
	 *
	 * @param sessionId WebSocket 会话ID
	 * @param session   原始 WebSocketSession
	 * @param userId    用户ID
	 */
	public void addSession(String sessionId, WebSocketSession session, String userId) {
		sessionMap.put(sessionId, new WsSession(session, userId, System.currentTimeMillis()));
	}

	/**
	 * 注册会话对应的 Redis 路由 Key 映射
	 *
	 * @param sessionId WebSocket 会话ID
	 * @param redisKey  Redis 路由 Key
	 */
	public void addSessionRedisKey(String sessionId, String redisKey) {
		sessionRedisKeyMap.put(sessionId, redisKey);
	}

	/**
	 * WS 断开时移除会话及其 Redis 路由映射
	 *
	 * @param sessionId WebSocket 会话ID
	 */
	public void removeSession(String sessionId) {
		sessionMap.remove(sessionId);
		sessionRedisKeyMap.remove(sessionId);
	}

	/**
	 * 刷新会话心跳时间（使用 computeIfPresent 保证线程安全）
	 *
	 * @param sessionId WebSocket 会话ID
	 */
	public void refreshHeartbeat(String sessionId) {
		sessionMap.computeIfPresent(sessionId, (k, v) -> {
			v.setLastHeartbeatTime(System.currentTimeMillis());
			return v;
		});
	}

	/**
	 * 获取会话关联的 Redis 路由 Key
	 *
	 * @param sessionId WebSocket 会话ID
	 * @return Redis 路由 Key，不存在返回 null
	 */
	public String getRedisKey(String sessionId) {
		return sessionRedisKeyMap.get(sessionId);
	}

	/**
	 * 获取指定 WsSession（不拷贝，直接引用）
	 *
	 * @param sessionId WebSocket 会话ID
	 * @return WsSession，不存在返回 null
	 */
	public WsSession getWsSession(String sessionId) {
		return sessionMap.get(sessionId);
	}

	// ==================== 查询 ====================

	/**
	 * 获取该用户在本机的所有在线 WebSocketSession
	 *
	 * @param userId 用户ID
	 * @return 在线 WebSocketSession 列表
	 */
	public List<WebSocketSession> getSessionsByUser(String userId) {
		return sessionMap.values().stream()
				.filter(w -> userId.equals(w.getUserId()))
				.map(WsSession::getWebSocketSession)
				.filter(WebSocketSession::isOpen)
				.collect(Collectors.toList());
	}

	/**
	 * 本机是否还有该用户的 session
	 *
	 * @param userId 用户ID
	 * @return true=本机仍有该用户在线
	 */
	public boolean hasUserSession(String userId) {
		return sessionMap.values().stream()
				.anyMatch(w -> userId.equals(w.getUserId()));
	}

	/**
	 * 获取当前在线会话数（用于监控/日志）
	 */
	public int getOnlineSessionCount() {
		return sessionMap.size();
	}

	/**
	 * 获取内存会话表（只读视图，供定时巡检任务使用）
	 */
	public Map<String, WsSession> getSessionMap() {
		return sessionMap;
	}

	// ==================== 广播 ====================

	/**
	 * 向所有在线 WebSocket 客户端广播消息
	 * <p>
	 * 遍历内存 Map 中所有活跃 Session，逐一推送 TextMessage。
	 * 发送失败的 Session（如连接已断开但未触发关闭回调）会被自动跳过。
	 * </p>
	 *
	 * @param message JSON 格式的消息字符串
	 */
	public void broadcastToAll(String message) {
		if (sessionMap.isEmpty()) {
			return;
		}
		TextMessage textMessage = new TextMessage(message);
		sessionMap.forEach((key, wsSession) -> {
			if (wsSession.getWebSocketSession().isOpen()) {
				try {
					wsSession.getWebSocketSession().sendMessage(textMessage);
				} catch (IOException e) {
					log.warn("Failed to send WS message to session [{}]: {}", key, e.getMessage());
					// 发送失败，标记清理（下次定时任务会处理）
				}
			}
		});
	}

	/**
	 * 按 Key 前缀批量推送消息
	 * <p>
	 * 遍历 sessionMap，筛选出 mapKey 以指定前缀开头的会话，批量发送消息。
	 * 支持按不同粒度匹配：
	 * <ul>
	 *   <li>按 userId 匹配：前缀 "admin:" → 推送给 admin 下所有设备/节点</li>
	 *   <li>按 userId + userDeviceId 匹配：前缀 "admin:phone-001:" → 推送给该设备的所有节点</li>
	 *   <li>精确匹配：前缀 "admin:phone-001:pilot-app" → 仅推送给该节点</li>
	 * </ul>
	 * </p>
	 *
	 * @param keyPrefix Key 前缀，如 "admin:" 或 "admin:phone-001:"
	 * @param message   JSON 格式的消息字符串
	 * @return 实际成功推送的会话数
	 */
	public int broadcastByKeyPrefix(String keyPrefix, String message) {
		if (sessionMap.isEmpty()) {
			return 0;
		}
		TextMessage textMessage = new TextMessage(message);
		int[] successCount = {0};
		sessionMap.forEach((key, wsSession) -> {
			if (key.startsWith(keyPrefix) && wsSession.getWebSocketSession().isOpen()) {
				try {
					wsSession.getWebSocketSession().sendMessage(textMessage);
					successCount[0]++;
				} catch (IOException e) {
					log.warn("Failed to send WS message to session [{}]: {}", key, e.getMessage());
				}
			}
		});
		log.debug("Batch send by prefix [{}]: success={}/total={}", keyPrefix, successCount[0], sessionMap.size());
		return successCount[0];
	}
}
