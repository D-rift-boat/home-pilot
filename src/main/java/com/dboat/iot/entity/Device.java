package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("iot_device")
public class Device {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String deviceId;

    private String deviceName;

    private String deviceModel;

    private String firmwareVersion;

    private String location;

    /**
     * Device status: 0=offline, 1=online, 2=abnormal
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
