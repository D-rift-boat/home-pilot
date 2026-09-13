package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * RBAC权限资源表（全局权限定义）
 * @TableName permission
 */
@TableName(value ="permission")
@Data
public class Permission implements Serializable {
    /**
     * 权限唯一UUID
     */
    @TableId
    private String permId;

    /**
     * 权限编码，鉴权核心，全局唯一，如iot:device:read
     */
    private String permCode;

    /**
     * 权限名称
     */
    private String permName;

    /**
     * 1菜单 2按钮/接口 3设备操作权限
     */
    private Integer permType;

    /**
     * 父权限ID，树形权限
     */
    private String parentPermId;

    /**
     * 排序
     */
    private Integer sortNum;

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