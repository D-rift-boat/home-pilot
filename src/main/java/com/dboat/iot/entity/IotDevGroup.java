package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * IoT设备分组表（树形）
 * @TableName iot_dev_group
 */
@TableName(value ="iot_dev_group")
@Data
public class IotDevGroup implements Serializable {
    /**
     * 分组全局唯一ID，直接用于Redis key
     */
    @TableId
    private String groupId;

    /**
     * 租户ID
     */
    private String orgId;

    /**
     * 分组名称
     */
    private String groupName;

    /**
     * 父分组ID，NULL代表根分组
     */
    private String parentGroupId;

    /**
     * 排序号
     */
    private Integer sortNum;

    /**
     * 0禁用，1正常
     */
    private Integer status;

    /**
     * 
     */
    private LocalDateTime createTime;

    /**
     * 
     */
    private LocalDateTime updateTime;

    /**
     * 
     */
    private LocalDateTime deleteTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}