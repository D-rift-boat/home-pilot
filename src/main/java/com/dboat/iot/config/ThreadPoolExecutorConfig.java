package com.dboat.iot.config;

import com.dboat.iot.config.properties.ThreadPoolProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.*;

@Slf4j
@Configuration
public class ThreadPoolExecutorConfig {

    @Resource
    private ThreadPoolProperties threadPoolProperties;

    /**
     * 通用业务线程池
     * @return ExecutorService
     */
    @Bean(name = "commonExecutor")
    public ExecutorService commonExecutor() {
        ThreadPoolProperties.PoolConfig config = threadPoolProperties.getCommon();
        ThreadFactory threadFactory = new CustomizableThreadFactory("common-pool-");
        ExecutorService executor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("【commonThreadPool】init finished core:{},max:{},queue:{}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity());
        return executor;
    }

    /**
     * WebSocket消息推送专用线程池（你的大屏WS推送使用）
     * @return ExecutorService
     */
    @Bean(name = "wsPushExecutor")
    public ExecutorService wsPushExecutor() {
        ThreadPoolProperties.PoolConfig config = threadPoolProperties.getWsPush();
        ThreadFactory threadFactory = new CustomizableThreadFactory("ws-push-pool-");
        ExecutorService executor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("【wsPushThreadPool】init finished core:{},max:{},queue:{}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity());
        return executor;
    }

    /**
     * Redis PubSub 消息转发回调专用线程池（配套你之前RedisMessageListenerContainer）
     * @return ExecutorService
     */
    @Bean(name = "redisRelayExecutor")
    public ExecutorService redisRelayExecutor() {
        ThreadPoolProperties.PoolConfig config = threadPoolProperties.getRedisRelay();
        ThreadFactory threadFactory = new CustomizableThreadFactory("redis-relay-pool-");
        ExecutorService executor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("【redisRelayThreadPool】init finished core:{},max:{},queue:{}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity());
        return executor;
    }

    /**
     * 应用优雅停机：关闭线程池，等待任务执行完成
     * @return ExecutorService
     */
    @Bean
    @DependsOn({"commonExecutor", "wsPushExecutor", "redisRelayExecutor"})
    public ExecutorService shutdownHook(ExecutorService commonExecutor,
                                        ExecutorService wsPushExecutor,
                                        ExecutorService redisRelayExecutor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownExecutor(commonExecutor, "commonExecutor");
            shutdownExecutor(wsPushExecutor, "wsPushExecutor");
            shutdownExecutor(redisRelayExecutor, "redisRelayExecutor");
        }));
        return commonExecutor;
    }

    /**
     * 关闭线程池
     */
    private void shutdownExecutor(ExecutorService executor, String poolName) {
        if (!executor.isShutdown()) {
            log.info("准备关闭线程池:{}，等待任务收尾", poolName);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("线程池{}超时，强制终止未完成任务", poolName);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("线程池 {} 已关闭", poolName);
        }
    }
}
