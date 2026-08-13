package com.dboat.iot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * home-pilot 物联网设备管理系统 —— 应用启动入口
 * <p>
 * 项目定位：面向 ESP32 等单片机的物联网设备管理平台，提供设备注册、传感器数据采集、
 * 指令下发、告警管理等功能。技术栈涵盖 Spring Boot 3.x + MyBatis-Plus + MQTT + InfluxDB + Redis。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class HomePilotApplication {

    /**
     * 应用程序主入口方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(HomePilotApplication.class, args);

        log.info("========================================");
        log.info("\uD83C\uDF89 Home‑Pilot service started successfully");
        log.info("✅ Total beans: {}", context.getBeanDefinitionCount());
        log.info("========================================");
    }
}
