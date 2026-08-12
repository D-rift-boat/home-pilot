package com.dboat.iot.dto.mqtt;

import lombok.Data;

import java.util.Map;

/**
 * MQTT 指令下达消息 DTO —— 对应下行消息类型 DOWN_CMD
 * <p>
 * 标准 header + payload 结构，后端通过 MQTT 主题 iot/cmd/{deviceId} 向设备下发控制指令。
 * 设备端收到后需原样带回 requestId 作为异步应答匹配。
 * </p>
 *
 * @author dboat
 */
@Data
public class MqttDownCmdMessage {

    /** 消息头，包含路由与指令元数据 */
    private Header header;

    /** 消息体，包含指令内容 */
    private Payload payload;

    /**
     * 消息头
     */
    @Data
    public static class Header {
        /** 消息类型，固定为 "DOWN_CMD" */
        private String msgType;
        /** 指令唯一ID（UUID），设备回执必须原样带回，异步应答匹配核心 */
        private String requestId;
        /** 目标设备唯一标识 */
        private String deviceId;
        /** 指令下发时间戳（毫秒级） */
        private Long timestamp;
        /** 指令超时时间（毫秒），超过未回执云端判定指令失败 */
        private Long timeout;
    }

    /**
     * 消息体
     */
    @Data
    public static class Payload {
        /**
         * 指令编码，统一枚举：
         * <ul>
         *   <li>device_restart：设备重启</li>
         *   <li>sensor_calibrate：传感器校准</li>
         *   <li>light_switch：外接灯光开关</li>
         * </ul>
         */
        private String cmdCode;
        /** 指令参数，不同 cmdCode 对应不同的参数结构 */
        private Map<String, Object> params;
    }
}
