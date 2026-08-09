package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备指令实体 —— 对应 MySQL 表 iot_device_command
 * <p>
 * 存储后端向设备下发的控制指令记录，每条指令通过 MQTT 主题 device/command/{device_id} 发送到设备。
 * 指令状态流转：0(待下发) → 1(已下发) → 2(执行成功) / 3(执行失败)
 * </p>
 *
 * @author dboat
 */
@Data
@TableName("iot_device_command")
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
     * 指令内容，如 "set_temp:25"、"restart" 等
     */
    private String command;

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
