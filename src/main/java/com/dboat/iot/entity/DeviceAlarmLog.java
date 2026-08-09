package com.dboat.iot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备告警日志实体 —— 对应 MySQL 表 iot_device_alarm_log
 * <p>
 * 存储设备触发告警的历史记录，包括传感器故障告警和设备离线告警。
 * 告警由流式告警引擎在 MQTT 消息处理过程中异步写入：
 * <ul>
 *   <li>传感器故障告警：数据上报时，在内存中检测 sensor_status/aht20_status/bmp280_status 异常后触发</li>
 *   <li>设备离线告警：收到 EMQX 系统事件（client disconnected）后触发</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Data
@TableName("iot_device_alarm_log")
public class DeviceAlarmLog {

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
     * 告警类型，取值对应 {@link com.dboat.iot.enums.AlarmTypeEnum} 的 name()
     * <p>如：SENSOR_FAULT（传感器故障）、DEVICE_OFFLINE（设备离线）</p>
     */
    private String alarmType;

    /**
     * 告警级别：1=提示（info），2=警告（warning），3=严重（critical）
     * <p>传感器故障通常为 2-警告，设备离线通常为 3-严重</p>
     */
    private Integer alarmLevel;

    /**
     * 告警详情 JSON Payload
     * <p>
     * 传感器故障时存储各传感器状态码和原始上报数据；
     * 设备离线时存储断连事件来源等信息。
     * </p>
     */
    private String alarmDetail;

    /**
     * 异常上下文信息（JSON 格式）
     * <p>如设备离线时，从 InfluxDB 查询的最后一条传感器数据，作为故障排查的辅助信息</p>
     */
    private String alarmContext;

    /**
     * 告警处理状态：0=未处理，1=已确认，2=已处理
     */
    private Integer handleStatus;

    /**
     * 处理备注，运维人员处理告警时填写的说明信息
     */
    private String handleRemark;

    /**
     * 告警触发时间（告警产生的精确时间）
     */
    private LocalDateTime triggerTime;

    /**
     * 告警处理时间（运维人员确认/处理的时间）
     */
    private LocalDateTime handleTime;

    /**
     * 记录创建时间（由 MyBatis-Plus 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
