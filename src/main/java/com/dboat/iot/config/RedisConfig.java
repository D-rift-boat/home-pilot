package com.dboat.iot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置类
 * <p>
 * 创建 StringRedisTemplate Bean，用于设备实时状态存储（State Store）。
 * 在 IoT 架构中，Redis 承担以下职责：
 * <ul>
 *   <li>维护设备在线状态（ONLINE/OFFLINE/ABNORMAL）</li>
 *   <li>记录设备最后活跃时间（last_seen）</li>
 *   <li>缓存最新传感器连接状态，避免高频上报写入 MySQL</li>
 * </ul>
 * Redis Key 规范：iot:device:state:{device_id}（Hash 结构）
 * </p>
 *
 * @author dboat
 */
@Configuration
public class RedisConfig {

    /**
     * 创建 StringRedisTemplate Bean
     * <p>
     * 使用 String 序列化方式，适合存储 JSON 字符串和简单的键值对。
     * 设备状态存储使用 Hash 结构（HSET/HGET），通过 {@link com.dboat.iot.utils.DeviceStateStore} 封装操作。
     * </p>
     *
     * @param connectionFactory Spring 自动注入的 Redis 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
