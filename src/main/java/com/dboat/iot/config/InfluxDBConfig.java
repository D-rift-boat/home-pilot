package com.dboat.iot.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Data
@Configuration
@Slf4j
@ConfigurationProperties(prefix = "influxdb")
public class InfluxDBConfig {

    private String url;
    private String token;
    private String orgName;
    private String bucket;
    private String logLevel;

    @Bean
    public InfluxDBClient influxDBClient() {
        // Okhttp日志拦截器，输出完整http body（Flux语句会打印在这里）
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
            log.info("[InfluxDB Http] {}", message);
        });
        // 日志级别：打印请求体，body完整Flux脚本会输出   生产用basic
        HttpLoggingInterceptor.Level influxLogLevel;
        try {
            influxLogLevel = HttpLoggingInterceptor.Level.valueOf(logLevel.trim().toUpperCase());
        } catch (Exception e) {
            log.warn("日志级别配置错误:{}，回退至BASIC", logLevel);
            influxLogLevel = HttpLoggingInterceptor.Level.BASIC;
        }
        loggingInterceptor.setLevel(influxLogLevel);

        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                .url(url)
                .authenticateToken(token.toCharArray())
                .org(orgName)
                .bucket(bucket)
                .okHttpClient(new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .addInterceptor(loggingInterceptor)
                )
                .build();
        return InfluxDBClientFactory.create(options);


    }
}
