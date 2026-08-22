package com.dboat.iot.ws;

import com.alibaba.fastjson2.JSON;
import com.dboat.iot.dto.ws.WsRelayMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WS中继消息处理器
 * 负责处理从Redis订阅的WS中继消息，并将消息中转给本地的WS会话
 * @author dboat
 * @date 2026/08/022
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WsRelayMessageHandler implements MessageListener {

    /**
     * 本地WS会话管理器
     */
    private final LocalWsSessionManager localWsSessionManager;

    /**
     * 处理从Redis订阅的WS中继消息
     * @param message 消息
     * @param pattern 模式
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            WsRelayMessageDTO relayMsg = JSON.parseObject(body, WsRelayMessageDTO.class);

            // 在本地sessionMap里找到该用户的所有session，逐个发送
            List<WebSocketSession> sessions = localWsSessionManager.getSessionsByUser(relayMsg.getUserId());
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(relayMsg.getPayload()));
                }
            }
            log.debug("Relay message delivered, userId={}, localSessionCount={}",
                    relayMsg.getUserId(), sessions.size());
        } catch (Exception e) {
            log.error("处理WS中继消息失败", e);
        }
    }
}
