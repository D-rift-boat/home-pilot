-- =============================================
-- 物联网设备管理系统 - MySQL 数据库脚本
-- IoT Device Management System - MySQL Schema
-- 数据库 Database: iot
-- =============================================

CREATE DATABASE IF NOT EXISTS `iot`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `iot`;

-- ----------------------------
-- 设备资产表 / Device asset table
-- 存储低频修改的静态属性
-- ----------------------------
DROP TABLE IF EXISTS `device`;
CREATE TABLE `device` (
    `id`               VARCHAR(64)   NOT NULL COMMENT '主键ID / Primary key (UUID)',
    `device_id`        VARCHAR(64)   NOT NULL COMMENT '设备唯一标识 / Unique device identifier (e.g. esp32-S3-001)',
    `device_name`      VARCHAR(100)  DEFAULT NULL COMMENT '设备名称 / Device name',
    `product_id`       VARCHAR(64)   DEFAULT NULL COMMENT '所属产品ID / Product ID',
    `device_model`     VARCHAR(50)   DEFAULT NULL COMMENT '设备型号 / Device model (e.g. ESP32-S3)',
    `firmware_version` VARCHAR(32)   DEFAULT NULL COMMENT '固件版本 / Firmware version',
    `location`         VARCHAR(128)  DEFAULT NULL COMMENT '安装位置 / Installation location',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间 / Updated time',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除 / Logical delete: 0=active, 1=deleted',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备资产表 / Device asset table';

-- ----------------------------
-- 设备指令表 / Device command table
-- ----------------------------
CREATE TABLE IF NOT EXISTS `device_command` (
    `id`          VARCHAR(64)   NOT NULL COMMENT '主键ID / Primary key (UUID)',
    `device_id`   VARCHAR(64)   NOT NULL COMMENT '关联设备标识 / Associated device_id',
    `command`     VARCHAR(255)  NOT NULL COMMENT '指令内容 / Command content (e.g. set_temp:25, restart)',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '指令状态: 0=待下发, 1=已下发, 2=执行成功, 3=执行失败 / Status: 0=pending, 1=sent, 2=success, 3=failed',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间 / Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备指令表 / Device command table';

-- ----------------------------
-- 设备告警日志表 / Device alarm log table
-- 存储设备触发告警的历史记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `device_alarm_log` (
    `id`               VARCHAR(64)   NOT NULL COMMENT '主键ID / Primary key (UUID)',
    `device_id`        VARCHAR(64)   NOT NULL COMMENT '关联设备标识 / Associated device_id',
    `alarm_type`       VARCHAR(64)   NOT NULL COMMENT '告警类型 / Alarm type (e.g. SENSOR_FAULT, DEVICE_OFFLINE)',
    `alarm_level`      TINYINT       NOT NULL DEFAULT 1 COMMENT '告警级别: 1=提示, 2=警告, 3=严重 / Alarm level: 1=info, 2=warning, 3=critical',
    `alarm_detail`     TEXT          DEFAULT NULL COMMENT '告警详情JSON / Alarm detail payload (JSON)',
    `alarm_context`    VARCHAR(512)  DEFAULT NULL COMMENT '异常上下文 / Exception context (e.g. last sensor state)',
    `handle_status`    TINYINT       NOT NULL DEFAULT 0 COMMENT '处理状态: 0=未处理, 1=已确认, 2=已处理 / Handle status: 0=unhandled, 1=acknowledged, 2=resolved',
    `handle_remark`    VARCHAR(255)  DEFAULT NULL COMMENT '处理备注 / Handle remark',
    `trigger_time`     DATETIME      NOT NULL COMMENT '触发时间 / Trigger time',
    `handle_time`      DATETIME      DEFAULT NULL COMMENT '处理时间 / Handle time',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
    PRIMARY KEY (`id`),
    KEY `idx_device_id` (`device_id`),
    KEY `idx_alarm_type` (`alarm_type`),
    KEY `idx_trigger_time` (`trigger_time`),
    KEY `idx_handle_status` (`handle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备告警日志表 / Device alarm log table';
