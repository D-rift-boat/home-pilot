package com.dboat.iot.ws;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 连接处理器
 * <p>
 * 核心职责：
 * <ul>
 *   <li>管理所有前端 WebSocket 客户端会话（内存 ConcurrentHashMap）</li>
 *   <li>Redis 路由表保活：ws:device:router:{userId}:{nodeId}，TTL 60s</li>
 *   <li>心跳保活：收到前端消息刷新 Redis TTL</li>
 *   <li>定时清理：遍历内存 Map，Redis Key 过期则移除 Session</li>
 *   <li>实时数据广播：broadcastToAll() 推送给所有在线 WS 客户端</li>
 * </ul>
 * </p>
 * <p>
 * 连接建立流程：
 * <ol>
 *   <li>前端连接 ws://host/ws?userId=admin&nodeId=web_xxx</li>
 *   <li>后端从 URL 参数提取 userId、nodeId</li>
 *   <li>注册到内存 Map（key=userId:nodeId → value=session）</li>
 *   <li>写入 Redis 路由 Key（TTL 60s）</li>
 * </ol>
 * </p>
 *
 * @author dboat
 */
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);

    /** Redis 路由 Key 前缀：ws:device:router:{userId}:{nodeId} */
    private static final String WS_ROUTER_PREFIX = "ws:device:router:";

    /** Redis 路由 Key TTL：60秒（前端30s心跳，留2倍余量） */
    private static final long WS_ROUTER_TTL_SECONDS = 60;

    /** 内存会话表：key = userId:userDeviceId:nodeId，value = WebSocketSession */
    private final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    /** Session 与路由 Key 的映射：sessionId → redisKey，用于关闭时清理 */
    private final ConcurrentHashMap<String, String> sessionKeyMap = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;

    public DeviceWebSocketHandler(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Resource
    private ObjectMapper objectMapper;

    // ==================== 连接生命周期 ====================

    /**
     * 连接建立成功
     * <p>
     * 从 URL 参数中提取 userId 和 nodeId，注册到内存 Map 和 Redis 路由表。
     * 连接地址示例：ws://localhost/ws?userId=admin&nodeId=web_192.168.1.100_chrome01
     * </p>
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        //String[] params = extractUrlParams(session);
        String userId = "admin";
        String userDeviceId = "phone-001";
        String nodeId = "pilot-app";
        String mapKey = userId + ":" +userDeviceId + ":" + nodeId;
        String redisKey = WS_ROUTER_PREFIX + userId + ":" + nodeId;

        // 注册到内存 Map
        sessionMap.put(mapKey, session);
        sessionKeyMap.put(session.getId(), redisKey);

        // 写入 Redis 路由 Key（TTL 60s）
        redisTemplate.opsForValue().set(redisKey, session.getId(), WS_ROUTER_TTL_SECONDS, TimeUnit.SECONDS);

        log.info("WS connected: userId={}, nodeId={}, sessionId={}, total={}", userId, nodeId, session.getId(), sessionMap.size());
    }

    /**
     * 收到前端文本消息（心跳 / 业务消息）
     * <p>
     * 每次收到消息刷新 Redis 路由 Key TTL，实现心跳保活。
     * 前端每 30s 发送一次心跳消息。
     * </p>
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JsonNode json = objectMapper.readTree(payload);
        String type = json.get("type").asText();
        String redisKey = sessionKeyMap.get(session.getId());
        if (redisKey != null) {
            // 刷新 Redis TTL
            redisTemplate.expire(redisKey, WS_ROUTER_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("WS heartbeat refreshed: sessionId={}, key={}", session.getId(), redisKey);
        }
        if("ping".equals(type)){
            // 收到ping，立刻回复pong
            String pong = "{\"type\":\"pong\"}";
            session.sendMessage(new TextMessage(pong));
            return;
        }

    }

    /**
     * 连接关闭（正常关闭 / 异常断开）
     * <p>
     * 清理内存 Map 和 Redis 路由 Key。
     * </p>
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String redisKey = sessionKeyMap.remove(session.getId());
        if (redisKey != null) {
            // 从 Redis 删除路由 Key
            redisTemplate.delete(redisKey);
            // 从内存 Map 中移除（反查 mapKey）
            sessionMap.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
            log.info("WS disconnected: sessionId={}, status={}, remaining={}", session.getId(), status, sessionMap.size());
        }
    }

    /**
     * 传输异常处理
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WS transport error: sessionId={}, error={}", session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // ==================== 广播 ====================

    /**
     * 向所有在线 WebSocket 客户端广播消息
     * <p>
     * 遍历内存 Map 中所有活跃 Session，逐一推送 TextMessage。
     * 发送失败的 Session（如连接已断开但未触发关闭回调）会被自动清理。
     * </p>
     *
     * @param message JSON 格式的消息字符串
     */
    public void broadcastToAll(String message) {
        if (sessionMap.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        sessionMap.forEach((key, session) -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
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
        sessionMap.forEach((key, session) -> {
            if (key.startsWith(keyPrefix) && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                    successCount[0]++;
                } catch (IOException e) {
                    log.warn("Failed to send WS message to session [{}]: {}", key, e.getMessage());
                }
            }
        });
        log.debug("Batch send by prefix [{}]: success={}/total={}", keyPrefix, successCount[0], sessionMap.size());
        return successCount[0];
    }

    // ==================== 定时清理 ====================

    /**
     * 定时清理过期会话（每 30 秒执行一次）
     * <p>
     * 遍历内存 Map，检查对应 Redis 路由 Key 是否存活：
     * - Key 不存在（已过期）→ 说明前端心跳中断，关闭 Session 并从 Map 移除
     * - Key 存在 → 连接正常，跳过
     * </p>
     */
    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void cleanExpiredSessions() {
        if (sessionMap.isEmpty()) {
            return;
        }
        sessionMap.forEach((key, session) -> {
            String redisKey = sessionKeyMap.get(session.getId());
            if (redisKey == null || Boolean.FALSE.equals(redisTemplate.hasKey(redisKey))) {
                // Redis Key 已过期，关闭并移除 Session
                log.info("Cleaning expired WS session: mapKey={}, sessionId={}", key, session.getId());
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.POLICY_VIOLATION);
                    }
                } catch (IOException e) {
                    log.warn("Error closing expired WS session [{}]: {}", key, e.getMessage());
                }
                sessionMap.remove(key);
                sessionKeyMap.remove(session.getId());
            }
        });
    }

    // ==================== 工具方法 ====================

    /**
     * 从 WebSocketSession 的 URI 中提取 userId 和 nodeId 参数
     *
     * @param session WebSocket 会话
     * @return [userId, nodeId]，缺失时使用默认值 admin / 匿名
     */
    //private String[] extractUrlParams(WebSocketSession session) {
    //    String userId = "admin";
    //    String nodeId = "anonymous";
    //
    //    URI uri = session.getUri();
    //    if (uri != null && uri.getQuery() != null) {
    //        String query = uri.getQuery();
    //        for (String param : query.split("&")) {
    //            String[] kv = param.split("=", 2);
    //            if (kv.length == 2) {
    //                String decodedValue = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
    //                if ("userId".equals(kv[0])) {
    //                    userId = decodedValue;
    //                } else if ("nodeId".equals(kv[0])) {
    //                    nodeId = decodedValue;
    //                }
    //            }
    //        }
    //    }
    //    return new String[]{userId, nodeId};
    //}

    /**
     * 获取当前在线会话数（用于监控/日志）
     */
    public int getOnlineSessionCount() {
        return sessionMap.size();
    }
}
