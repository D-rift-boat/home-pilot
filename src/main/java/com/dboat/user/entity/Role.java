package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * RBAC角色表（租户级角色）
 * @TableName role
 */
@TableName(value ="role")
@Data
public class Role implements Serializable {
    /**
     * 角色唯一UUID
     */
    @TableId
    private String roleId;

    /**
     * 租户ID，角色归属于租户
     */
    private String orgId;

    /**
     * 角色编码，程序鉴权使用，同org内唯一
     */
    private String roleCode;

    /**
     * 角色展示名称
     */
    private String roleName;

    /**
     * 备注描述
     */
    private String remark;

    /**
     * 0禁用 1启用
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