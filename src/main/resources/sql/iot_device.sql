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
-- 存储标准 DOWN_CMD 格式指令记录，支持 requestId 异步应答匹配
-- ----------------------------
CREATE TABLE IF NOT EXISTS `device_command` (
    `id`          VARCHAR(64)   NOT NULL COMMENT '主键ID / Primary key (UUID)',
    `device_id`   VARCHAR(64)   NOT NULL COMMENT '关联设备标识 / Associated device_id',
    `request_id`  VARCHAR(64)   DEFAULT NULL COMMENT '指令唯一ID(UUID) / DOWN_CMD header.requestId',
    `cmd_code`    VARCHAR(64)   NOT NULL COMMENT '指令编码 / Command code (e.g. device_restart, sensor_calibrate, light_switch)',
    `params`      TEXT          DEFAULT NULL COMMENT '指令参数JSON / Command params (DOWN_CMD payload.params)',
    `timeout`     BIGINT        DEFAULT 5000 COMMENT '指令超时时间(ms) / Command timeout in milliseconds',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '指令状态: 0=待下发, 1=已下发, 2=执行成功, 3=执行失败 / Status: 0=pending, 1=sent, 2=success, 3=failed',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间 / Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_device_id` (`device_id`),
    KEY `idx_request_id` (`request_id`)
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


-- ----------------------------
-- 用户-设备关系表 / User-Device relation table
-- ----------------------------
DROP TABLE IF EXISTS `user_dev_rel`;
CREATE TABLE `user_dev_rel` (
                                        `id`          VARCHAR(64)  NOT NULL COMMENT '主键ID / Primary key (UUID)',
                                        `user_id`     VARCHAR(64)  NOT NULL COMMENT '用户ID / User ID',
                                        `device_id`   VARCHAR(64)  NOT NULL COMMENT '设备ID / Device ID',
                                        `sub_type`    TINYINT      NOT NULL DEFAULT 1 COMMENT '订阅类型: 1=拥有者, 2=共享订阅 / Subscription type',
                                        `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 / Created time',
                                        `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间 / Updated time',
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_user_device` (`user_id`, `device_id`),
                                        KEY `idx_user_id` (`user_id`),
                                        KEY `idx_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-设备关系表 / User-Device relation table';







-- =============================================
-- 多租户IoT平台 MySQL建表脚本（兼容已有3张旧表，不重复创建device_info / device_log / device_command，按需ALTER追加字段）
-- 规范：
-- 1. 已有表：device_info、device_log、device_command 保留原有结构，ALTER增加org_id、适配多租户；不删不改原有业务字段
-- 2. 新增表：org、user、iot_group、iot_device_group_rel、iot_group_user_rel
-- 3. 命名：顶层平台表不带iot_，IoT业务关联表带iot_
-- 4. 时间字段：create_time/update_time/delete_time，datetime，和你原有表风格对齐，不强行改成datetime(3)
-- 5. 全部业务表增加org_id，多租户隔离，查询强制带上 org_id AND deleted=0（旧表）/ delete_time IS NULL（新表）
-- 6. 旧表软删除标识：device_info用tinyint deleted=0/1，保持原样；新表统一delete_time(datetime)软删除
-- =============================================


-- --------------------------
-- 原有业务表追加租户ID，不改动原有任何字段
-- --------------------------
ALTER TABLE `device_info`
    ADD COLUMN `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID，多租户隔离',
ADD INDEX `idx_org_device_id` (`org_id`,`device_id`);

ALTER TABLE `device_log`
    ADD COLUMN `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID，多租户隔离',
ADD INDEX `idx_org_device_id` (`org_id`,`device_id`),
ADD INDEX `idx_org_log_time` (`org_id`,`log_time`);

ALTER TABLE `device_command`
    ADD COLUMN `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID，多租户隔离',
ADD INDEX `idx_org_device_id` (`org_id`,`device_id`);

-- --------------------------
-- 租户组织（顶层表，无前缀）
-- --------------------------
CREATE TABLE `user_org` (
                       `org_id` VARCHAR(64) NOT NULL COMMENT '租户全局唯一ID，Redis key使用',
                       `org_name` VARCHAR(128) NOT NULL COMMENT '租户名称',
                       `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用，1正常',
                       `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                       `delete_time` DATETIME NULL DEFAULT NULL COMMENT '软删除时间，NULL=未删除',
                       PRIMARY KEY (`org_id`),
                       KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户组织表';

-- --------------------------
-- user 用户主表（RBAC主体，去掉sys_）
-- --------------------------
CREATE TABLE `user` (
                        `user_id` VARCHAR(64) NOT NULL COMMENT '用户全局唯一UUID主键',
                        `org_id` VARCHAR(64) NOT NULL COMMENT '所属租户ID，多租户隔离核心',
                        `username` VARCHAR(128) NOT NULL COMMENT '平台登录账号',
                        `email` VARCHAR(128) NULL COMMENT '邮箱',
                        `nickname` VARCHAR(64) NULL COMMENT '昵称',
                        `avatar_url` VARCHAR(255) NULL COMMENT '头像地址',
                        `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1正常 2锁定',
                        `token_version` INT NOT NULL DEFAULT 0 COMMENT '令牌版本号，+1后该用户所有已签发token全部失效（踢人/封号）',
                        `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
                        `lock_expire_time` DATETIME NULL COMMENT '锁定截止时间，到期自动解锁',
                        `last_login_time` DATETIME NULL COMMENT '最后登录时间',
                        `last_login_ip` VARCHAR(32) NULL COMMENT '最后登录IP',
                        `first_login` TINYINT NOT NULL DEFAULT 1 COMMENT '1首次登录(强制改密) 0非首次',
                        `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        `delete_time` DATETIME NULL DEFAULT NULL,
                        PRIMARY KEY (`user_id`),
                        UNIQUE KEY `uk_org_username` (`org_id`,`username`),
                        KEY `idx_org_status` (`org_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户主表（RBAC主体）';

-- --------------------------
-- user_auth 用户认证凭据表（多登录方式：密码/手机/邮箱/OAuth）
-- --------------------------
CREATE TABLE `user_auth` (
                             `auth_id` BIGINT AUTO_INCREMENT NOT NULL,
                             `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
                             `user_id` VARCHAR(64) NOT NULL COMMENT '关联user.user_id',
                             `auth_type` TINYINT NOT NULL COMMENT '1密码 2手机验证码 3邮箱 4第三方OAuth',
                             `identifier` VARCHAR(128) NOT NULL COMMENT '账号标识：手机号/邮箱/openid',
                             `credential` VARCHAR(128) NULL COMMENT '凭据：BCrypt哈希 / 第三方token',
                             `password_version` INT NOT NULL DEFAULT 0 COMMENT '密码版本，修改密码后旧token失效',
                             `last_password_change` DATETIME NULL COMMENT '最后改密时间',
                             `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             `delete_time` DATETIME NULL DEFAULT NULL,
                             PRIMARY KEY (`auth_id`),
                             UNIQUE KEY `uk_org_identifier_type` (`org_id`,`identifier`,`auth_type`),
                             KEY `idx_org_user_id` (`org_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证凭据表（登录凭据与用户业务解耦）';

-- --------------------------
-- role 角色表（租户级角色，不同租户角色隔离）
-- --------------------------
CREATE TABLE `role` (
                        `role_id` VARCHAR(64) NOT NULL COMMENT '角色唯一UUID',
                        `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID，角色归属于租户',
                        `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码，程序鉴权使用，同org内唯一',
                        `role_name` VARCHAR(128) NOT NULL COMMENT '角色展示名称',
                        `remark` VARCHAR(255) NULL COMMENT '备注描述',
                        `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
                        `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        `delete_time` DATETIME NULL DEFAULT NULL,
                        PRIMARY KEY (`role_id`),
                        UNIQUE KEY `uk_org_role_code` (`org_id`,`role_code`),
                        KEY `idx_org_status` (`org_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色表（租户级角色）';

-- --------------------------
-- permission 权限资源表（全局权限编码，菜单/接口/按钮权限）
-- --------------------------
CREATE TABLE `permission` (
                              `perm_id` VARCHAR(64) NOT NULL COMMENT '权限唯一UUID',
                              `perm_code` VARCHAR(128) NOT NULL COMMENT '权限编码，鉴权核心，全局唯一，如iot:device:read',
                              `perm_name` VARCHAR(128) NOT NULL COMMENT '权限名称',
                              `perm_type` TINYINT NOT NULL COMMENT '1菜单 2按钮/接口 3设备操作权限',
                              `parent_perm_id` VARCHAR(64) NULL COMMENT '父权限ID，树形权限',
                              `sort_num` INT NOT NULL DEFAULT 0 COMMENT '排序',
                              `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
                              `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              `delete_time` DATETIME NULL DEFAULT NULL,
                              PRIMARY KEY (`perm_id`),
                              UNIQUE KEY `uk_perm_code` (`perm_code`),
                              KEY `idx_parent_perm` (`parent_perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC权限资源表（全局权限定义）';

-- --------------------------
-- user_role 用户角色关联（多对多中间表）
-- --------------------------
CREATE TABLE `user_role` (
                             `id` VARCHAR(64) NOT NULL COMMENT 'UUID主键',
                             `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
                             `user_id` VARCHAR(64) NOT NULL COMMENT 'user.user_id',
                             `role_id` VARCHAR(64) NOT NULL COMMENT 'role.role_id',
                             `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             `delete_time` DATETIME NULL DEFAULT NULL,
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_org_user_role` (`org_id`,`user_id`,`role_id`),
                             KEY `idx_org_user` (`org_id`,`user_id`),
                             KEY `idx_org_role` (`org_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- --------------------------
-- role_perm 角色权限关联（多对多中间表）
-- --------------------------
CREATE TABLE `role_perm` (
                             `id` VARCHAR(64) NOT NULL COMMENT 'UUID主键',
                             `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
                             `role_id` VARCHAR(64) NOT NULL COMMENT 'role.role_id',
                             `perm_id` VARCHAR(64) NOT NULL COMMENT 'permission.perm_id',
                             `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             `delete_time` DATETIME NULL DEFAULT NULL,
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_org_role_perm` (`org_id`,`role_id`,`perm_id`),
                             KEY `idx_org_role` (`org_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- --------------------------
-- iot_group IoT设备分组表（树形分组，iot_业务前缀）
-- --------------------------
CREATE TABLE `iot_dev_group` (
                             `group_id` VARCHAR(64) NOT NULL COMMENT '分组全局唯一ID，直接用于Redis key',
                             `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
                             `group_name` VARCHAR(128) NOT NULL COMMENT '分组名称',
                             `parent_group_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '父分组ID，NULL代表根分组',
                             `sort_num` INT NOT NULL DEFAULT 0 COMMENT '排序号',
                             `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用，1正常',
                             `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             `delete_time` DATETIME NULL DEFAULT NULL,
                             PRIMARY KEY (`group_id`),
                             UNIQUE KEY `uk_org_group` (`org_id`,`group_name`),
                             KEY `idx_org_parent` (`org_id`,`parent_group_id`),
                             KEY `idx_org_status` (`org_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT设备分组表（树形）';

-- --------------------------
-- iot_device_group_rel 设备分组多对多关联
-- --------------------------
CREATE TABLE `iot_dev_group_rel` (
                                        `id` VARCHAR(64) NOT NULL COMMENT 'UUID主键',
                                        `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
                                        `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID，关联device_info.device_id',
                                        `group_id` VARCHAR(64) NOT NULL COMMENT '分组ID',
                                        `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        `delete_time` DATETIME NULL DEFAULT NULL,
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_org_device_group` (`org_id`,`device_id`,`group_id`),
                                        KEY `idx_org_group_device` (`org_id`,`group_id`,`device_id`),
                                        KEY `idx_org_device` (`org_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备和分组关联表（多对多）';

-- --------------------------
-- iot_group_user_rel 分组用户业务权限（设备分组层面的细粒度权限，叠加RBAC）
-- --------------------------
CREATE TABLE `iot_group_user_rel` (
                                      `id` VARCHAR(64) NOT NULL COMMENT '主键',
                                      `org_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
                                      `group_id` VARCHAR(64) NOT NULL COMMENT '分组ID',
                                      `user_id` VARCHAR(64) NOT NULL COMMENT 'user.user_id',
                                      `role_code` VARCHAR(32) NOT NULL COMMENT '分组角色编码 admin/operator/viewer',
                                      `perms` JSON NULL COMMENT '分组内权限集合JSON，和Redis缓存保持一致',
                                      `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      `delete_time` DATETIME NULL DEFAULT NULL,
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_org_group_user` (`org_id`,`group_id`,`user_id`),
                                      KEY `idx_org_user` (`org_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组-用户业务权限关联表';

CREATE TABLE auth_login_log (
                                id VARCHAR(64) NOT NULL COMMENT '主键UUID',
                                user_id VARCHAR(64) COMMENT '登录用户ID，登录失败场景可为NULL',
                                identifier VARCHAR(128) COMMENT '登录标识：手机号/邮箱/账号',
                                login_type TINYINT COMMENT '登录类型：1密码 2验证码 3第三方授权',
                                login_ip VARCHAR(32) COMMENT '登录客户端IP',
                                user_agent VARCHAR(255) COMMENT 'UA客户端信息',
                                channel TINYINT DEFAULT 1 COMMENT '渠道：1Web 2App 3开放API',
                                result TINYINT NOT NULL COMMENT '登录结果：0失败 1成功',
                                fail_reason VARCHAR(128) COMMENT '失败原因：密码错误/账号锁定/验证码错误',
                                create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (id),
                                KEY idx_uid_time (user_id, create_time),
                                KEY idx_ip_time (login_ip, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录审计日志表';

CREATE TABLE sys_audit_log (
                               id VARCHAR(64) NOT NULL COMMENT '主键UUID',
                               user_id VARCHAR(64) NOT NULL COMMENT '操作人用户ID',
                               org_id VARCHAR(64) NOT NULL COMMENT '租户ID',
                               action VARCHAR(64) NOT NULL COMMENT '操作动作：device.delete / shadow.set / service.invoke',
                               resource_type VARCHAR(32) COMMENT '资源类型：product/device/group/role/user',
                               resource_id VARCHAR(64) COMMENT '操作资源ID',
                               request_ip VARCHAR(32) COMMENT '请求客户端IP',
                               request_uri VARCHAR(255) COMMENT '请求接口地址',
                               request_method VARCHAR(8) COMMENT '请求方式 GET/POST/PUT/DELETE',
                               params TEXT COMMENT '请求入参JSON，敏感字段脱敏存储',
                               result TINYINT COMMENT '操作结果：0失败 1成功',
                               error_msg VARCHAR(255) COMMENT '异常信息',
                               cost_ms INT COMMENT '接口耗时(ms)',
                               create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
                               PRIMARY KEY (id),
                               KEY idx_uid_time (user_id, create_time),
                               KEY idx_org_time (org_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作审计日志表';
