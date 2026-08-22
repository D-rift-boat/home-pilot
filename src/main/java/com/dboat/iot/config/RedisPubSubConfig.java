package com.dboat.iot.config;

import com.dboat.iot.config.generator.NodeIdProvider;
import com.dboat.iot.ws.WsRelayMessageHandler;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis发布订阅配置
 */
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    /**
     * 节点ID提供器
     */
    @Resource
    private NodeIdProvider nodeIdProvider;

    /**
     * WS中继消息处理器
     */
    private final WsRelayMessageHandler relayMessageHandler;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 只订阅本节点专属channel，不订阅全局channel
        container.addMessageListener(
                relayMessageHandler,
                new ChannelTopic(nodeIdProvider.getRelayChannel())
        );
        return container;
    }
}
