package com.dboat.iot.service.ws;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dboat.iot.config.generator.NodeIdProvider;
import com.dboat.iot.dto.ws.WsRelayMessageDTO;
import com.dboat.iot.ws.LocalWsSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dboat.iot.common.constants.WebSocketConstants.WS_RELAY_PREFIX;

/**
 * WS分布式推送服务
 * 负责将消息推送给指定用户的所有WS连接
 * @author: dboat
 * @date: 2026/08/22
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WsDistributedPushService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NodeIdProvider nodeIdProvider;
    //private final WsUserNodeMappingService userNodeMappingService;
    private final LocalWsSessionManager localWsSessionManager;
    private final WsSessionRoutingService wsSessionRoutingService;

    /**
     * 给指定userId的所有WS连接推送消息（分布式集群版）
     */
    @Async("commonExecutor")
    public void pushToUser(String userId, String msgType, Object payload) {
        // 如果 payload 已经是 JSON 字符串，直接使用；否则序列化
        String payloadJson = (payload instanceof String) ? (String) payload : JSON.toJSONString(payload);

        // 1.查询该用户在哪些节点有WS连接（从WS路由表获取）
        Map<String, JSONObject> sessionNodeMap = wsSessionRoutingService.getUserSessionNodeMap(userId);
        if (ObjectUtils.isEmpty(sessionNodeMap)) {
            log.debug("user {} has no online ws connections", userId);
            return;
        }

        Set<String> targetNodes = sessionNodeMap.values().stream()
                .map(json -> json.getString("nodeId"))
                .collect(Collectors.toSet());
        WsRelayMessageDTO relayMsg = WsRelayMessageDTO.builder()
                .userId(userId)
                .msgType(msgType)
                .payload(payloadJson)
                .build();
        String relayMsgJson = JSON.toJSONString(relayMsg);

        // 2.循环向每个目标节点的专属channel publish
        for (String nodeId : targetNodes) {
            if (nodeId.equals(nodeIdProvider.getLocalNodeId())) {
                // 目标是本机，直接本地推送，不走Redis Pub/Sub
                pushLocal(userId, payloadJson);
            } else {
                // 目标是远端节点，publish到该节点专属channel
                redisTemplate.convertAndSend(WS_RELAY_PREFIX + nodeId, relayMsgJson);
            }
        }
    }

    /**
     * 本机直接推送
     */
    private void pushLocal(String userId, String payloadJson) {
        List<WebSocketSession> sessions = localWsSessionManager.getSessionsByUser(userId);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payloadJson));
                }
            } catch (Exception e) {
                log.error("本地WS推送失败, sessionId={}", session.getId(), e);
            }
        }
    }
}
