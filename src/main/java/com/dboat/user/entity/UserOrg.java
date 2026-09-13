package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户组织表
 * @TableName org
 */
@TableName(value ="user_org")
@Data
public class UserOrg implements Serializable {
    /**
     * 租户全局唯一ID，Redis key使用
     */
    @TableId
    private String orgId;

    /**
     * 租户名称
     */
    private String orgName;

    /**
     * 0禁用，1正常
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 软删除时间，NULL=未删除
     */
    private LocalDateTime deleteTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}