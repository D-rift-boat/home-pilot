package com.dboat.iot.ws;

import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.dto.ws.WsUploadDataDTO;
import com.dboat.iot.enums.WsTypeEnum;
import com.dboat.iot.service.ws.WsDistributedPushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.dboat.iot.common.constants.WebSocketConstants.*;

/**
 * WebSocket 连接处理器
 * <p>
 * 核心职责：
 * <ul>
 *   <li>处理 WebSocket 连接生命周期（建立 / 消息 / 关闭 / 异常）</li>
 *   <li>Redis 路由表保活：ws:session:{userId}，TTL 60s</li>
 *   <li>心跳保活：收到前端消息刷新 Redis TTL</li>
 *   <li>本地会话管理委托 {@link LocalWsSessionManager} 统一处理</li>
 * </ul>
 * </p>
 * <p>
 * 连接建立流程：
 * <ol>
 *   <li>前端连接 ws://host/ws?userId=admin&nodeId=web_xxx</li>
 *   <li>后端从 URL 参数提取 userId、nodeId</li>
 *   <li>通过 LocalWsSessionManager 注册到内存 Map</li>
 *   <li>写入 Redis 路由 Key（TTL 60s）</li>
 * </ol>
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private final StringRedisTemplate redisTemplate;

    /** 本地会话管理器，统一管理所有内存会话状态 */
    @Resource
    private LocalWsSessionManager localWsSessionManager;

    /** WS分布式推送服务，用户级精准推送 */
    @Resource
    private WsDistributedPushService wsPushService;

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
        String redisKey = WS_ROUTER_PREFIX + userId;

        // 通过 LocalWsSessionManager 注册会话
        localWsSessionManager.addSession(session.getId(), session, userId);
        localWsSessionManager.addSessionRedisKey(session.getId(), redisKey);

        // 写入 Redis 路由 Key（TTL 60s）
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("nodeId", nodeId);
        hashMap.put("lastHeartbeatTs", String.valueOf(System.currentTimeMillis()));
        HashMap<String, Object> map = new HashMap<>();
        map.put(session.getId(), JSONObject.toJSONString(hashMap));
        redisTemplate.opsForHash().putAll(redisKey, map);
        redisTemplate.expire(redisKey, WS_ROUTER_TTL_SECONDS, TimeUnit.SECONDS);

        // 用户上线 ws通知用户在线设备数量
        Long userDeviceOnlineCount = redisTemplate.opsForHash().size(redisKey);
        WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
        wsUploadDataDTO.setType("USER_DEVICE_ONLINE_COUNT");
        WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
        WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
        deviceDTO.setDeviceId("web-001");
        dataDTO.setUserDeviceOnlineCount(String.valueOf(userDeviceOnlineCount));
        wsUploadDataDTO.setData((dataDTO));
        wsUploadDataDTO.setDevice(deviceDTO);
        wsUploadDataDTO.setData((dataDTO));
        // 用户级推送：通知该用户在线设备数量变更
        wsPushService.pushToUser(userId, WsTypeEnum.USER_DEVICE_ONLINE_COUNT.getCode(), wsUploadDataDTO);

        log.info("WS connected: userId={}, nodeId={}, sessionId={}, total={}", userId, nodeId, session.getId(), localWsSessionManager.getOnlineSessionCount());
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
        String redisKey = localWsSessionManager.getRedisKey(session.getId());

        // 通过 LocalWsSessionManager 刷新心跳时间（computeIfPresent 保证线程安全）
        localWsSessionManager.refreshHeartbeat(session.getId());

        if (redisKey != null) {
            // 刷新 Redis TTL
            redisTemplate.expire(redisKey, WS_ROUTER_TTL_SECONDS, TimeUnit.SECONDS);
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("nodeId", "pilot-app");
            hashMap.put("lastHeartbeatTs", String.valueOf(System.currentTimeMillis()));
            HashMap<String, Object> map = new HashMap<>();
            map.put(session.getId(), JSONObject.toJSONString(hashMap));
            redisTemplate.opsForHash().putAll(redisKey, map);
            log.debug("WS heartbeat refreshed: sessionId={}, key={}", session.getId(), redisKey);
            // 心跳包处理
            if ("ping".equals(type)) {
                // 收到ping，立刻回复pong
                String pong = "{\"type\":\"pong\"}";
                session.sendMessage(new TextMessage(pong));
            }
        } else {
            // 心跳超时后收到前端异常发来的消息处理 由后端巡检任务处理 关闭连接
            log.info("WS heartbeat missing: sessionId={}, key={}", session.getId(), redisKey);
        }
    }

    /**
     * 连接关闭（正常关闭 / 异常断开）
     * <p>
     * 清理内存 Map 和 Redis 路由 Key。
     * 用户在线设备数 -1
     * 本地session、redis session 删除
     * 用户会话key 删除
     * </p>
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String redisSessionKey = localWsSessionManager.getRedisKey(session.getId());
        if (redisSessionKey != null) {
            // 先提取 userId，用于后续用户级推送
            WsSession wsSession = localWsSessionManager.getWsSession(session.getId());
            String userId = wsSession != null ? wsSession.getUserId() : null;

            // 从 Redis 删除路由 sessionKey
            redisTemplate.opsForHash().delete(redisSessionKey, session.getId());
            // 通过 LocalWsSessionManager 移除本地会话 + redisKey映射
            localWsSessionManager.removeSession(session.getId());

            // 用户下线 推送该用户在线设备数量变更
            if (userId != null) {
                Long userDeviceOnlineCount = redisTemplate.opsForHash().size(redisSessionKey);
                WsUploadDataDTO wsUploadDataDTO = new WsUploadDataDTO();
                wsUploadDataDTO.setType(WsTypeEnum.USER_DEVICE_ONLINE_COUNT.getCode());
                WsUploadDataDTO.DataDTO dataDTO = new WsUploadDataDTO.DataDTO();
                WsUploadDataDTO.DeviceDTO deviceDTO = new WsUploadDataDTO.DeviceDTO();
                deviceDTO.setDeviceId("web-001");
                dataDTO.setUserDeviceOnlineCount(String.valueOf(userDeviceOnlineCount));
                wsUploadDataDTO.setData((dataDTO));
                wsUploadDataDTO.setDevice(deviceDTO);
                wsPushService.pushToUser(userId, WsTypeEnum.USER_DEVICE_ONLINE_COUNT.getCode(), wsUploadDataDTO);
            }
            log.info("WS disconnected: sessionId={}, status={}, remaining={}", session.getId(), status, localWsSessionManager.getOnlineSessionCount());
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
}
