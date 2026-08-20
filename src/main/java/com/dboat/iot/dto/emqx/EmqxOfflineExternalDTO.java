package com.dboat.iot.dto.emqx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 外部WebHook：仅【连接断开】回调使用
 */
@Data
public class EmqxOfflineExternalDTO {
    @JsonProperty("event")
    private String event;

    @JsonProperty("clientid")
    private String clientid;

    @JsonProperty("username")
    private String username;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("disconnected_at")
    private Long disconnectedAt;

    @JsonProperty("ip_address")
    private String ipAddress;
}