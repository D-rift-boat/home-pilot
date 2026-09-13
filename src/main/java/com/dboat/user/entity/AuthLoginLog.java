package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 登录审计日志表
 * @TableName auth_login_log
 */
@TableName(value ="auth_login_log")
@Data
public class AuthLoginLog implements Serializable {
    /**
     * 主键UUID
     */
    @TableId
    private String id;

    /**
     * 登录用户ID，登录失败场景可为NULL
     */
    private String userId;

    /**
     * 登录标识：手机号/邮箱/账号
     */
    private String identifier;

    /**
     * 登录类型：1密码 2验证码 3第三方授权
     */
    private Integer loginType;

    /**
     * 登录客户端IP
     */
    private String loginIp;

    /**
     * UA客户端信息
     */
    private String userAgent;

    /**
     * 渠道：1Web 2App 3开放API
     */
    private Integer channel;

    /**
     * 登录结果：0失败 1成功
     */
    private Integer result;

    /**
     * 失败原因：密码错误/账号锁定/验证码错误
     */
    private String failReason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}