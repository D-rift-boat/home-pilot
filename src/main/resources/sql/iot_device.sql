-- =============================================
-- 物联网设备管理系统 - MySQL 数据库脚本
-- IoT Device Management System - MySQL Schema
-- 数据库 Database: iot_device
-- =============================================

CREATE DATABASE IF NOT EXISTS `iot_device`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `iot_device`;

-- ----------------------------
-- 设备信息表 / Device info table
-- ----------------------------
CREATE TABLE IF NOT EXISTS `iot_device` (
    `id`               VARCHAR(64)   NOT NULL COMMENT '主键ID / Primary key (UUID)',
    `device_id`        VARCHAR(64)   NOT NULL COMMENT '设备唯一标识 / Unique device identifier',
    `device_name`      VARCHAR(100)  DEFAULT NULL COMMENT '设备名称 / Device name',
    `device_model`     VARCHAR(50)   DEFAULT NULL COMMENT '设备型号 / Device model (e.g. ESP32-S3)',
    `firmware_version` VARCHAR(32)   DEFAULT NULL COMMENT '固件版本 / Firmware version',
    `location`         VARCHAR(128)  DEFAULT NULL COMMENT '安装位置 / Installation location',
    `status`           TINYINT       NOT NULL DEFAULT 1 COMMENT '设备状态: 0=离线, 1=在线, 2=异常 / Status: 0=offline, 1=online, 2=abnormal',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间 / Updated time',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除 / Logical delete: 0=active, 1=deleted',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表 / Device info table';

-- ----------------------------
-- 设备指令表 / Device command table
-- ----------------------------
CREATE TABLE IF NOT EXISTS `iot_device_command` (
    `id`          VARCHAR(64)   NOT NULL COMMENT '主键ID / Primary key (UUID)',
    `device_id`   VARCHAR(64)   NOT NULL COMMENT '关联设备标识 / Associated device_id',
    `command`     VARCHAR(255)  NOT NULL COMMENT '指令内容 / Command content (e.g. set_temp:25, restart)',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '指令状态: 0=待下发, 1=已下发, 2=执行成功, 3=执行失败 / Status: 0=pending, 1=sent, 2=success, 3=failed',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间 / Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备指令表 / Device command table';
