package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户角色关联表
 * @TableName user_role
 */
@TableName(value ="user_role")
@Data
public class UserRole implements Serializable {
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
     * user.user_id
     */
    private String userId;

    /**
     * role.role_id
     */
    private String roleId;

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