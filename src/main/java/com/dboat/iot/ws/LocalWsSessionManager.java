package com.dboat.iot.ws;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.dboat.iot.common.constants.WebSocketConstants.WS_CONN_PREFIX;

/**
 * 本地 WebSocket 会话管理器
 * <p>
 * 基于 Caffeine 本地缓存维护 sessionId → WsSession 的内存会话表。
 * 本类只负责本节点会话的内存视图，不再维护 Redis 路由映射（分布式路由由其他组件负责）。
 * </p>
 * <p>
 * 生命周期设计：
 * <ul>
 *   <li>正常断开：WebSocket 关闭回调主动调用 removeSession()</li>
 *   <li>异常断开（网络闪断、客户端崩溃，关闭回调不触发）：由 Caffeine 访问过期策略兜底驱逐僵尸会话，避免内存泄漏</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Component
@Slf4j
public class LocalWsSessionManager {

    /**
     * 本地会话缓存：key = sessionId, value = WsSession
     * 访问过期 90 秒：90 秒内无心跳访问则自动驱逐（兜底清理无关闭回调的僵尸连接）
     */
    private Cache<String, WsSession> sessionCache;

    /**
     * key = userId, value = 当前本机该用户所有sessionId集合
     * 访问过期90s；用户长时间无心跳，整个集合自动过期清理
     */
    private Cache<String, Set<String>> userSessionCache;

    @PostConstruct
    public void init() {
        sessionCache = Caffeine.newBuilder()
                // 访问过期：心跳刷新会触发访问，重置倒计时；正常在线会话不会被误驱逐
                .expireAfterAccess(48, TimeUnit.SECONDS)
                // 驱逐监听器：缓存条目被移除/过期时回调，兜底释放资源
                .removalListener((String sessionId, WsSession wsSession, RemovalCause cause) -> {
                    if (wsSession == null) {
                        return;
                    }
                    WebSocketSession rawSession = wsSession.getWebSocketSession();
                    log.debug("WS session evicted, sessionId={}, userId={}, cause={}",
                            sessionId, wsSession.getUserId(), cause);
                    // 兜底关闭仍未断开的会话，防止僵尸连接残留
                    if (rawSession.isOpen()) {
                        try {
                            rawSession.close();
                        } catch (IOException e) {
                            log.error("Close evicted session error, sessionId={}", sessionId, e);
                        }
                    }

                    //清除user set中的sessionId
                    String userId = wsSession.getUserId();
                    if(userId != null){
                        Set<String> set = userSessionCache.getIfPresent(userId);
                        if(set != null){
                            set.remove(sessionId);
                        }
                    }
                })
                // 开启统计，供监控指标采集
                .recordStats()
                .build();

        //invalidate 会异步回调 removalListener
        userSessionCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(48))
                .build();
    }

    // ==================== 会话生命周期 ====================

    /**
     * WS 连接建立时注册会话
     *
     * @param sessionId WebSocket 会话ID
     * @param session   原始 WebSocketSession
     * @param userId    用户ID
     */
    public void addSession(String sessionId, WebSocketSession session, String userId) {
        WsSession wsSession = new WsSession(session, userId, System.currentTimeMillis());
        sessionCache.put(sessionId, wsSession);
        Set<String> sessionIdSet = userSessionCache.get(userId, k -> new HashSet<>());
        sessionIdSet.add(sessionId);
    }

    /**
     * WS 断开时移除会话（正常关闭回调调用）
     *
     * @param sessionId WebSocket 会话ID
     */
    public void removeSession(String sessionId) {
        sessionCache.invalidate(sessionId);
    }

    /**
     * 刷新会话心跳时间
     * <p>
     * 关键点：通过 getIfPresent 触发 Caffeine 访问，重置 expireAfterAccess 倒计时。
     * 不能只修改 WsSession 内部 lastHeartbeatTime，否则缓存感知不到访问，正常在线会话会被误驱逐。
     * </p>
     *
     * @param sessionId WebSocket 会话ID
     */
    public void refreshHeartbeat(String sessionId) {
        WsSession wsSession = sessionCache.getIfPresent(sessionId);
        if (wsSession != null) {
            wsSession.setLastHeartbeatTime(System.currentTimeMillis());
            String userId = wsSession.getUserId();
            if(userId != null){
                userSessionCache.getIfPresent(userId);
            }
        }
    }

    /**
     * 获取指定 WsSession
     *
     * @param sessionId WebSocket 会话ID
     * @return WsSession，不存在返回 null
     */
    public WsSession getWsSession(String sessionId) {
        return sessionCache.getIfPresent(sessionId);
    }

    // ==================== 查询 ====================

    /**
     * 获取该用户在本机的所有在线 WebSocketSession
     *
     * @param userId 用户ID
     * @return 在线 WebSocketSession 列表
     */
    public List<WebSocketSession> getSessionsByUser(String userId) {
        Set<String> sessionIdSet = userSessionCache.getIfPresent(userId);
        if (ObjectUtils.isEmpty(sessionIdSet)) {
            return Collections.emptyList();
        }
        List<WebSocketSession> result = new ArrayList<>();
        for (String sid : sessionIdSet) {
            WsSession session = sessionCache.getIfPresent(sid);
            if(ObjectUtils.isNotEmpty(session) && session.getWebSocketSession().isOpen()){
                result.add(session.getWebSocketSession());
            }
        }
        return result;
    }

    /**
     * 本机是否还有该用户的 session
     *
     * @param userId 用户ID
     * @return true=本机仍有该用户在线
     */
    public boolean hasUserSession(String userId) {
        return sessionCache.asMap().values().stream()
                .anyMatch(w -> userId.equals(w.getUserId()));
    }

    /**
     * 获取当前在线会话数（估算值，用于监控/日志）
     */
    public int getOnlineSessionCount() {
        return (int) sessionCache.estimatedSize();
    }

    /**
     * 获取内存会话表（只读视图，供定时巡检任务使用）
     */
    public Map<String, WsSession> getSessionMap() {
        return sessionCache.asMap();
    }

    // ==================== 广播 ====================

    /**
     * 向所有在线 WebSocket 客户端广播消息
     *
     * @param message JSON 格式的消息字符串
     */
    public void broadcastToAll(String message) {
        if (sessionCache.estimatedSize() == 0) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        sessionCache.asMap().forEach((sessionId, wsSession) -> {
            WebSocketSession rawSession = wsSession.getWebSocketSession();
            if (rawSession.isOpen()) {
                try {
                    rawSession.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("Failed to send WS message to session [{}]: {}", sessionId, e.getMessage());
                }
            }
        });
    }

    /**
     * 按 sessionId 前缀批量推送消息
     *
     * @param keyPrefix sessionId 前缀
     * @param message   JSON 格式的消息字符串
     * @return 实际成功推送的会话数
     */
    public int broadcastByKeyPrefix(String keyPrefix, String message) {
        if (sessionCache.estimatedSize() == 0) {
            return 0;
        }
        TextMessage textMessage = new TextMessage(message);
        int[] successCount = {0};
        sessionCache.asMap().forEach((sessionId, wsSession) -> {
            if (sessionId.startsWith(keyPrefix)) {
                WebSocketSession rawSession = wsSession.getWebSocketSession();
                if (rawSession.isOpen()) {
                    try {
                        rawSession.sendMessage(textMessage);
                        successCount[0]++;
                    } catch (IOException e) {
                        log.warn("Failed to send WS message to session [{}]: {}", sessionId, e.getMessage());
                    }
                }
            }
        });
        log.debug("Batch send by prefix [{}]: success={}/total={}", keyPrefix, successCount[0], sessionCache.estimatedSize());
        return successCount[0];
    }

    /**
     * 获取user的  redis session key
     * @param userId
     * @return
     */
    public String getRedisUserKey(String userId) {
        return  String.format(WS_CONN_PREFIX, userId);
    }
}
