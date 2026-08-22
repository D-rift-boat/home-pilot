package com.dboat.iot.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中继消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsRelayMessageDTO {
    /** 目标用户ID */
    private String userId;
    /** 推送的消息内容（JSON字符串） */
    private String payload;
    /** 消息类型，方便前端区分 */
    private String msgType;
}
