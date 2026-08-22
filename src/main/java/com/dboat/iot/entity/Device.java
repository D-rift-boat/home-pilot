package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dboat.iot.service.ws.DeviceStateService;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备信息实体 —— 对应 MySQL 表 device_info
 * <p>
 * 存储设备的静态元数据属性（低频修改），如设备标识、名称、型号、固件版本等。
 * 设备的实时在线状态存储在 Redis 中（参见 {@link DeviceStateService}），
 * 不在此表中维护，避免高频上报导致 MySQL 压力。
 * </p>
 *
 * @author dboat
 */
@Data
@TableName("device_info")
public class Device {

    /**
     * 主键ID（UUID 格式，由 MyBatis-Plus 自动生成）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 设备唯一标识（业务ID），如 "esp32-S3-001"
     * <p>对应 MQTT 主题 iot/sensor/upload/{device_id} 中的 {device_id} 部分</p>
     */
    private String deviceId;

    /**
     * 设备名称（可读性描述），如 "客厅温湿度传感器"
     */
    private String deviceName;

    /**
     * 所属产品ID，用于设备分组/产品分类管理
     */
    private String productId;

    /**
     * 设备型号，如 "ESP32-S3"
     */
    private String deviceModel;

    /**
     * 固件版本号，如 "1.0.0"，用于 OTA 升级管理
     */
    private String firmwareVersion;

    /**
     * 设备安装位置描述，如 "客厅"、"机房A"
     */
    private String location;

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

    /**
     * 逻辑删除标记：0=未删除（正常），1=已删除
     * <p>使用 MyBatis-Plus 的 @TableLogic 注解，执行 DELETE 时自动更新为 1</p>
     */
    @TableLogic
    private Integer deleted;
}
