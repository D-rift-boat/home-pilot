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
-- 设备信息表 / Device info table
-- 存储设备的静态属性（低频修改）
-- ----------------------------
DROP TABLE IF EXISTS `device_info`;
CREATE TABLE `device_info` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表 / Device info table';

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
-- 设备日志表 / Device log table
-- 记录设备上下线、异常等事件日志
-- ----------------------------
CREATE TABLE IF NOT EXISTS `device_log` (
    `id`               VARCHAR(64)   NOT NULL COMMENT '主键ID / Primary key (UUID)',
    `device_id`        VARCHAR(64)   NOT NULL COMMENT '关联设备标识 / Associated device_id',
    `log_type`         VARCHAR(32)   NOT NULL COMMENT '日志类型: ONLINE=上线, OFFLINE=离线, ABNORMAL=异常 / Log type',
    `log_detail`       TEXT          DEFAULT NULL COMMENT '日志详情JSON / Log detail payload (JSON)',
    `abnormal_status`  INT           DEFAULT NULL COMMENT '异常状态码 / Abnormal status code (e.g. sensor_status)',
    `abnormal_desc`    VARCHAR(512)  DEFAULT NULL COMMENT '异常描述 / Abnormal description',
    `log_time`         DATETIME      NOT NULL COMMENT '日志时间 / Log time',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
    PRIMARY KEY (`id`),
    KEY `idx_device_id` (`device_id`),
    KEY `idx_log_type` (`log_type`),
    KEY `idx_log_time` (`log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备日志表 / Device log table';

