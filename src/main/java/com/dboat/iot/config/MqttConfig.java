package com.dboat.iot.config;

import lombok.Data;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 连接配置类
 * <p>
 * 从 application.yml 中读取 mqtt.* 前缀的配置项，
 * 构建 MQTT 连接选项 Bean。支持配置 Broker 地址、认证信息、
 * 心跳间隔、自动重连、订阅主题等参数。
 * </p>
 * <p>
 * 实际 MQTT 客户端的创建和主题订阅由 {@link com.dboat.iot.mqtt.MqttClientManager} 完成。
 * EMQX 系统事件主题（设备断连感知）在 MqttClientManager 中自动追加订阅。
 * </p>
 *
 * @author dboat
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {

    /** MQTT Broker 连接地址，如 tcp://localhost:1883 */
    private String brokerUrl;

    /** MQTT 客户端唯一标识，用于 Broker 识别连接 */
    private String clientId;

    /** MQTT 认证用户名（可选） */
    private String username;

    /** MQTT 认证密码（可选） */
    private String password;

    /** 连接超时时间（秒），默认 30 秒 */
    private int connectionTimeout = 30;

    /** 心跳间隔（秒），默认 60 秒，用于维持连接活跃 */
    private int keepAliveInterval = 60;

    /** 是否启用自动重连，默认 true，断连后自动尝试重新连接 */
    private boolean automaticReconnect = true;

    /** 是否清除会话，默认 true，每次连接不保留离线期间的消息 */
    private boolean cleanSession = true;

    /** 需要订阅的 MQTT 主题列表（业务主题），如 iot/sensor/upload/+ */
    private String[] subscribeTopics;

    /** 各订阅主题对应的 QoS 等级（0=最多一次, 1=至少一次, 2=恰好一次） */
    private int[] subscribeQos;

    /**
     * 创建 MQTT 连接选项 Bean
     * <p>
     * 将配置属性封装为 Eclipse Paho 的 MqttConnectOptions 对象，
     * 供 MqttClientManager 初始化客户端时使用。
     * </p>
     *
     * @return MqttConnectOptions 连接选项实例
     */
    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        // 设置 Broker 地址
        options.setServerURIs(new String[]{brokerUrl});

        // 设置认证信息（用户名和密码，为空则不设置）
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }

        // 设置连接参数
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setAutomaticReconnect(automaticReconnect);
        options.setCleanSession(cleanSession);
        return options;
    }
}
