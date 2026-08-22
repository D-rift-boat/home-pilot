package com.dboat.iot.config;

import com.dboat.iot.config.generator.NodeIdProvider;
import com.dboat.iot.ws.WsRelayMessageHandler;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Redis发布订阅配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    /**
     * 节点ID提供器
     */
    @Resource
    private NodeIdProvider nodeIdProvider;

    /**
     * ws 推送线程池
     */
    @Resource
    private ExecutorService wsPushExecutor;

    /**
     * 中继订阅线程池
     */
    @Resource
    private ExecutorService redisRelayExecutor;

    /**
     * WS中继消息处理器
     */
    private final WsRelayMessageHandler relayMessageHandler;

    @Bean(destroyMethod = "destroy")
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // ========= 生产必配：分离两套独立有界线程池（重点！） ==========
        // 1. subscriptionExecutor：负责 SUBSCRIBE / UNSUBSCRIBE 订阅指令（网络层，小线程）
        container.setSubscriptionExecutor(redisRelayExecutor);

        // 2. taskExecutor：消息回调业务线程（推送WebSocket，核心隔离）
        container.setTaskExecutor(wsPushExecutor);

        // ========== 超时控制：等待订阅注册最大超时，防止启动阻塞 ==========
        container.setMaxSubscriptionRegistrationWaitingTime(3000);

        // ========== 注册监听器 + 本节点专属频道 ==========
        String relayChannel = nodeIdProvider.getRelayChannel();
        container.addMessageListener(relayMessageHandler, new ChannelTopic(relayChannel));

        // ========== 可选：生命周期监听，打印启动停止日志，方便运维排查 ==========
        // 方式：包装Bean生命周期回调
        log.info("准备启动Redis PubSub监听容器，订阅频道：{}", relayChannel);
        // 启动完成回调可以使用 SmartLifecycle start 扩展，或者直接后置日志
        return container;
    }
}
