package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 设备和分组关联表（多对多）
 * @TableName iot_dev_group_rel
 */
@TableName(value ="iot_dev_group_rel")
@Data
public class IotDevGroupRel implements Serializable {
    /**
     * UUID主键
     */
    @TableId
    private String id;

    /**
     * 租户ID
     */
    private String orgId;

    /**
     * 设备ID，关联device_info.device_id
     */
    private String deviceId;

    /**
     * 分组ID
     */
    private String groupId;

    /**
     * 
     */
    private LocalDateTime createTime;

    /**
     * 
     */
    private LocalDateTime deleteTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}