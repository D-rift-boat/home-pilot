package com.dboat.iot.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "thread-pool")
@Data
public class ThreadPoolProperties {

    private PoolConfig common = new PoolConfig();
    private PoolConfig wsPush = new PoolConfig();
    private PoolConfig redisRelay = new PoolConfig();

    @Data
    public static class PoolConfig {
        private int coreSize = 4;
        private int maxSize = 16;
        private int queueCapacity = 500;
        private long keepAliveSeconds = 60;
    }
}
