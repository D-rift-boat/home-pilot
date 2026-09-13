package com.dboat.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统操作审计日志表
 * @TableName sys_audit_log
 */
@TableName(value ="sys_audit_log")
@Data
public class SysAuditLog implements Serializable {
    /**
     * 主键UUID
     */
    @TableId
    private String id;

    /**
     * 操作人用户ID
     */
    private String userId;

    /**
     * 租户ID
     */
    private String orgId;

    /**
     * 操作动作：device.delete / shadow.set / service.invoke
     */
    private String action;

    /**
     * 资源类型：product/device/group/role/user
     */
    private String resourceType;

    /**
     * 操作资源ID
     */
    private String resourceId;

    /**
     * 请求客户端IP
     */
    private String requestIp;

    /**
     * 请求接口地址
     */
    private String requestUri;

    /**
     * 请求方式 GET/POST/PUT/DELETE
     */
    private String requestMethod;

    /**
     * 请求入参JSON，敏感字段脱敏存储
     */
    private String params;

    /**
     * 操作结果：0失败 1成功
     */
    private Integer result;

    /**
     * 异常信息
     */
    private String errorMsg;

    /**
     * 接口耗时(ms)
     */
    private Integer costMs;

    /**
     * 操作时间
     */
    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}