package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("iot_device_command")
public class DeviceCommand {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String deviceId;

    private String command;

    /**
     * Command status: 0=pending, 1=sent, 2=success, 3=failed
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
