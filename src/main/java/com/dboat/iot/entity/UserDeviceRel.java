package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-设备关系实体 —— 对应 MySQL 表 user_device_rel
 * <p>
 * 记录用户与设备之间的订阅关系，支持拥有者和共享订阅两种类型。
 * 当 IoT 设备上线时，通过该表查询订阅者列表，用于通知相关用户。
 * 订阅关系同时缓存在 Redis Set 中（Key: iot:device:sub:{deviceId}），
 * 设备上线时优先从 Redis 读取，缓存未命中时回源查询数据库并回填缓存。
 * </p>
 *
 * @author dboat
 */
@Data
@TableName("user_device_rel")
public class UserDeviceRel {

    /**
     * 主键ID（UUID 格式，由 MyBatis-Plus 自动生成）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 用户ID，关联系统用户
     */
    private String userId;

    /**
     * 设备ID（业务标识），如 "esp32-S3-001"
     */
    private String deviceId;

    /**
     * 订阅类型: 1=拥有者, 2=共享订阅
     */
    private Integer subType;

    /**
     * 记录创建时间（由 MyBatis-Plus 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间（由 MyBatis-Plus 自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
