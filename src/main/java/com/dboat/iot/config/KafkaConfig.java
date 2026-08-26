package com.dboat.iot.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 配置类
 * <p>
 * 生产者/消费者基础配置由 Spring Boot 自动装配（读取 spring.kafka.* 配置，
 * 自动创建 KafkaTemplate 与批量监听容器工厂）。
 * 本类额外提供：
 * <ul>
 *   <li>应用启动时自动创建遥测数据 Topic 与死信 Topic（已存在则跳过）</li>
 *   <li>消费失败错误处理器：重试耗尽后将失败消息转发至死信 Topic，避免阻塞消费进度</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Configuration
public class KafkaConfig {

    /** 传感器遥测数据 Topic 名称 */
    @Value("${kafka.topic.telemetry-data-topic}")
    private String telemetryTopic;

    /** 消费失败死信 Topic 名称 */
    @Value("${kafka.topic.dlq}")
    private String dlqTopic;

    /**
     * 自动创建传感器遥测数据 Topic
     * <p>4 分区、1 副本（本地开发环境；生产环境副本数按集群节点数调整）</p>
     */
    @Bean
    public NewTopic telemetryTopic() {
        return TopicBuilder.name(telemetryTopic).partitions(4).replicas(1).build();
    }

    /**
     * 自动创建死信 Topic（存放消费失败的遥测消息，便于后续排查与重放）
     */
    @Bean
    public NewTopic telemetryDlqTopic() {
        return TopicBuilder.name(dlqTopic).partitions(3).replicas(1).build();
    }

    /**
     * 消费错误处理器
     * <p>
     * 单条消息处理异常时最多重试 2 次（间隔 1 秒），重试耗尽后
     * 将消息转发至死信 Topic（分区与源消息保持一致），
     * 避免毒消息阻塞 Topic 消费、偏移量无法提交。
     * </p>
     *
     * @param kafkaTemplate Kafka 发送模板（Spring Boot 自动装配）
     * @return 消费错误处理器
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 死信转发器：失败消息投递至配置的死信 Topic，保留原分区
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new TopicPartition(dlqTopic, record.partition()));
        // FixedBackOff(间隔, 最大重试次数)
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }
}
