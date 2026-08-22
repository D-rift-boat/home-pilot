package com.dboat.iot.config;

import com.dboat.iot.utils.DeviceStateService;
import jakarta.annotation.Resource;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 生产级配置类
 * <p>
 * 在 IoT 架构中，Redis 承担以下职责：
 * <ul>
 *   <li>维护设备在线状态（ONLINE/OFFLINE/ABNORMAL）</li>
 *   <li>记录设备最后活跃时间（last_seen）</li>
 *   <li>缓存最新传感器连接状态，避免高频上报写入 MySQL</li>
 * </ul>
 * Redis Key 规范：iot:device:state:{device_id}（Hash 结构）
 * </p>
 * <p>
 * 序列化策略：
 * <ul>
 *   <li>Key / HashKey：StringRedisSerializer（可读性好，便于 redis-cli 调试）</li>
 *   <li>Value / HashValue：GenericJackson2JsonRedisSerializer（JSON 格式，自带类型信息，反序列化无需指定 Class）</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Configuration
public class RedisConfig {

    @Resource
    private RedissonClient redissonClient;

    @Bean("scriptExecutor")
    public RScript scriptExecutor() {
        return redissonClient.getScript(StringCodec.INSTANCE);
    }

    /**
     * 创建 RedisTemplate&lt;String, Object&gt; Bean
     * <p>
     * 通用 Redis 操作模板，支持存储任意 Java 对象（自动 JSON 序列化）。
     * Key 使用 String 序列化，Value 使用 JSON 序列化（含类型信息）。
     * </p>
     *
     * @param connectionFactory Spring 自动注入的 Redis 连接工厂
     * @return 配置好序列化器的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ===== Key 序列化：StringRedisSerializer（可读、紧凑） =====
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);


        // ===== Value 序列化：GenericJackson2JsonRedisSerializer（JSON + 类型信息） =====
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建 StringRedisTemplate Bean
     * <p>
     * 专用于纯字符串操作场景（如设备状态 Hash 存储），
     * Key 和 Value 均使用 String 序列化，性能最优。
     * 设备状态存储使用 Hash 结构（HSET/HGET），
     * 通过 {@link DeviceStateService} 封装操作。
     * </p>
     *
     * @param connectionFactory Spring 自动注入的 Redis 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 构建 JSON 序列化器
     * <p>
     * 使用 GenericJackson2JsonRedisSerializer 默认构造器，
     * 内部自动在 JSON 中写入 @class 类型信息，反序列化时自动还原 Java 类型，
     * 支持常见 JDK 类型（String、Number、Date、Map、List 等）的序列化/反序列化。
     * </p>
     *
     * @return GenericJackson2JsonRedisSerializer 实例
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }
}
