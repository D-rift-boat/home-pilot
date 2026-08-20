package com.dboat.iot.dto.emqx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 外部WebHook：仅【连接建立】回调使用
 */
@Data
public class EmqxOnlineExternalDTO {
    @JsonProperty("event")
    private String event;

    @JsonProperty("clientid")
    private String clientid;

    @JsonProperty("username")
    private String username;

    @JsonProperty("ip_address")
    private String ipAddress;
}