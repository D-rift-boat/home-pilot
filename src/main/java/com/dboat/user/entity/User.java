package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统用户主表（RBAC主体）
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User implements Serializable {
    /**
     * 用户全局唯一UUID主键
     */
    @TableId
    private String userId;

    /**
     * 所属租户ID，多租户隔离核心
     */
    private String orgId;

    /**
     * 平台登录账号
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 0禁用 1正常 2锁定
     */
    private Integer status;

    /**
     * 令牌版本号，+1后该用户所有已签发token全部失效（踢人/封号）
     */
    private Integer tokenVersion;

    /**
     * 连续登录失败次数
     */
    private Integer loginFailCount;

    /**
     * 锁定截止时间，到期自动解锁
     */
    private LocalDateTime lockExpireTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    /**
     * 1首次登录(强制改密) 0非首次
     */
    private Integer firstLogin;

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