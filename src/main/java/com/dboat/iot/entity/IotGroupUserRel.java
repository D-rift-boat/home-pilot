package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 分组-用户业务权限关联表
 * @TableName iot_group_user_rel
 */
@TableName(value ="iot_group_user_rel")
@Data
public class IotGroupUserRel implements Serializable {
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
     * 分组ID
     */
    private String groupId;

    /**
     * user.user_id
     */
    private String userId;

    /**
     * 分组角色编码 admin/operator/viewer
     */
    private String roleCode;

    /**
     * 分组内权限集合JSON，和Redis缓存保持一致
     */
    private Object perms;

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