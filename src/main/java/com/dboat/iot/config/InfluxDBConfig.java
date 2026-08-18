package com.dboat.iot.config;

import com.influxdb.client.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * InfluxDB 时序数据库配置类
 * <p>
 * 从 application.yml 中读取 influxdb.* 前缀的配置项，
 * 创建 InfluxDB Client Bean，用于传感器遥测数据的读写操作。
 * 内置 OkHttp 日志拦截器，支持通过配置调整 HTTP 日志级别。
 * </p>
 *
 * @author dboat
 */
@Data
@Configuration
@Slf4j
@ConfigurationProperties(prefix = "influxdb")
public class InfluxDBConfig {

    /** InfluxDB 服务地址，如 http://localhost:8086 */
    private String url;

    /** InfluxDB API 访问令牌（Token 认证） */
    private String token;

    /** InfluxDB 组织名称 */
    private String orgName;

    /** 默认写入的 Bucket 名称，如 "iot" */
    private String bucket;

    /**
     * OkHttp 日志级别配置（如 BODY、BASIC、HEADERS、NONE）
     * <p>开发环境建议用 BODY（打印完整 Flux 查询语句），生产环境建议用 BASIC</p>
     */
    private String logLevel;

    /**
     * 创建 InfluxDB 客户端 Bean
     * <p>
     * 配置了连接超时、读写超时、日志拦截器等参数。
     * 该客户端是线程安全的，全局复用即可。
     * </p>
     *
     * @return InfluxDBClient 实例
     */
    @Bean
    public InfluxDBClient influxDBClient() {
        // OkHttp 日志拦截器，输出完整 HTTP 请求/响应信息（Flux 查询语句会打印在这里）
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
            log.info("[InfluxDB Http] {}", message);
        });

        // 解析配置的日志级别，配置错误时自动降级为 BASIC
        HttpLoggingInterceptor.Level influxLogLevel;
        try {
            influxLogLevel = HttpLoggingInterceptor.Level.valueOf(logLevel.trim().toUpperCase());
        } catch (Exception e) {
            log.warn("日志级别配置错误:{}，回退至BASIC", logLevel);
            influxLogLevel = HttpLoggingInterceptor.Level.BASIC;
        }
        loggingInterceptor.setLevel(influxLogLevel);

        // 构建 InfluxDB 客户端连接选项
        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                .url(url)
                .authenticateToken(token.toCharArray())
                .org(orgName)
                .bucket(bucket)
                .okHttpClient(new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)   // 连接超时 10 秒
                        .readTimeout(30, TimeUnit.SECONDS)       // 读取超时 30 秒（查询可能较慢）
                        .writeTimeout(10, TimeUnit.SECONDS)      // 写入超时 10 秒
                        .addInterceptor(loggingInterceptor)      // 挂载日志拦截器
                )
                .build();
        return InfluxDBClientFactory.create(options);
    }
}
