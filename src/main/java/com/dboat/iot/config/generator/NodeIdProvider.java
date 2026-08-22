package com.dboat.iot.config.generator;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

/**
 * 节点id 生成器
 */
@Slf4j
@Component
public class NodeIdProvider {

    @Value("${app.node-id:}")
    private String configNodeId;

    /**
     * 本节点的id
     */
    private String localNodeId;

    @PostConstruct
    public void init() {
        if (configNodeId != null && !configNodeId.isBlank()) {
            localNodeId = configNodeId;
        } else {
            try {
                String host = InetAddress.getLocalHost().getHostAddress().replace(".", "-");
                String shortUuid = UUID.randomUUID().toString().substring(0, 8);
                localNodeId = "node-" + host + "-" + shortUuid;
            } catch (Exception e) {
                log.warn("Failed to get local host address, using random UUID instead", e);
                localNodeId = "node-" + UUID.randomUUID().toString().substring(0, 8);
            }
        }
    }

    /**
     * 获取本节点的id
     * @return
     */
    public String getLocalNodeId() {
        return localNodeId;
    }

    /**
     * 本节点订阅的relay channel
     */
    public String getRelayChannel() {
        return "ws:relay:" + localNodeId;
    }
}
