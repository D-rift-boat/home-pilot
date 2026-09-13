package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户认证凭据表（登录凭据与用户业务解耦）
 * @TableName user_auth
 */
@TableName(value ="user_auth")
@Data
public class UserAuth implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long auth_id;

    /**
     * 租户ID
     */
    private String orgId;

    /**
     * 关联user.user_id
     */
    private String userId;

    /**
     * 1密码 2手机验证码 3邮箱 4第三方OAuth
     */
    private Integer authType;

    /**
     * 账号标识：手机号/邮箱/openid
     */
    private String identifier;

    /**
     * 凭据：BCrypt哈希 / 第三方token
     */
    private String credential;

    /**
     * 密码版本，修改密码后旧token失效
     */
    private Integer passwordVersion;

    /**
     * 最后改密时间
     */
    private LocalDateTime lastPasswordChange;

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