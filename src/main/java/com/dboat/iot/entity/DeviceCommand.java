package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备指令实体 —— 对应 MySQL 表 device_command
 * <p>
 * 存储后端向设备下发的控制指令记录，每条指令通过 MQTT 主题 iot/cmd/{deviceId} 发送到设备。
 * 指令采用标准 DOWN_CMD 格式（header + payload），支持 requestId 异步应答匹配。
 * 指令状态流转：0(待下发) → 1(已下发) → 2(执行成功) / 3(执行失败)
 * </p>
 *
 * @author dboat
 */
@Data
@TableName("device_command")
public class DeviceCommand {

    /**
     * 主键ID（UUID 格式，由 MyBatis-Plus 自动生成）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 目标设备唯一标识（对应 Device.deviceId）
     */
    private String deviceId;

    /**
     * 指令唯一ID（UUID），对应 DOWN_CMD header.requestId，设备回执必须原样带回
     */
    private String requestId;

    /**
     * 指令编码，统一枚举：device_restart / sensor_calibrate / light_switch
     */
    private String cmdCode;

    /**
     * 指令参数（JSON 字符串），对应 DOWN_CMD payload.params
     */
    private String params;

    /**
     * 指令超时时间（毫秒），超过未回执云端判定指令失败
     */
    private Long timeout;

    /**
     * 指令执行状态：0=待下发（pending），1=已下发（sent），2=执行成功（success），3=执行失败（failed）
     */
    private Integer status;

    /**
     * 记录创建时间（由 MyBatis-Plus 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间（由 MyBatis-Plus 自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
