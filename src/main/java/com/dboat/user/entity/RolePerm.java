package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色权限关联表
 * @TableName role_perm
 */
@TableName(value ="role_perm")
@Data
public class RolePerm implements Serializable {
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
     * role.role_id
     */
    private String roleId;

    /**
     * permission.perm_id
     */
    private String permId;

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