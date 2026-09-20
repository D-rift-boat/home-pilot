package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备日志实体 —— 对应 MySQL 表 device_log
 * <p>
 * 记录设备上下线、异常等事件日志：
 * <ul>
 *   <li>ONLINE：设备上线（传感器数据首次上报时记录）</li>
 *   <li>OFFLINE：设备离线（EMQX 断连事件触发）</li>
 *   <li>ABNORMAL：设备异常（传感器状态码异常时记录）</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("device_log")
public class DeviceLog {

    /**
     * 主键ID（UUID 格式，由 MyBatis-Plus 自动生成）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 关联设备唯一标识（对应 Device.deviceId）
     */
    private String deviceId;

    /**
     * 关联追踪唯一标识（对应 traceId）
     */
    private String traceId;
    /**
     * orgId
     */
    private String orgId;

    /**
     * 日志类型：ONLINE=上线, OFFLINE=离线, ABNORMAL=异常
     */
    private String logType;

    /**
     * 日志详情 JSON（如上线时的传感器状态、离线时的最后数据等）
     */
    private String logDetail;

    /**
     * 异常状态码（传感器异常时记录 sensor_status 值）
     */
    private Integer abnormalStatus;

    /**
     * 异常描述（传感器异常时的详细说明）
     */
    private String abnormalDesc;

    /**
     * 日志时间（事件发生的精确时间）
     */
    private LocalDateTime logTime;

    /**
     * 记录创建时间（由 MyBatis-Plus 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
