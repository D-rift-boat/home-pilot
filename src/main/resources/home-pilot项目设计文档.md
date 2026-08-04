# 物联网设备管理系统 SpringBoot 项目设计文档

# 一、项目整体架构设计

## 1\.1 架构概述

本项目基于 SpringBoot 3\.x \+ JDK21 构建，聚焦物联网设备接入、数据采集与基础管理，采用分层架构设计，核心遵循“高内聚、低耦合”原则，预留 Redis、Kafka 扩展接口，支持后期平滑升级。整体架构分为五层，新增 API 层与 DTO 层，各层职责清晰，互不干扰。

## 1\.2 核心架构分层

- **API 层**：提供 RESTful 风格 HTTP 接口，负责请求接收、参数校验、响应封装，对接前端系统与第三方平台，优先支持设备数据查询相关接口。

- **DTO 层**：数据传输对象层，封装接口请求与响应数据，实现实体类与外部数据的解耦，避免敏感字段泄露，规范数据传输格式。

- **业务层**：核心业务逻辑处理层，包含设备管理、数据解析、MQTT 消息处理、InfluxDB 数据写入与查询等模块，通过 MyBatis\-Plus 实现数据库操作，封装业务逻辑，降低与数据层的耦合。

- **数据访问层**：基于 MyBatis\-Plus 实现 MySQL 相关操作，负责设备元数据、指令数据的增删改查；同时集成 InfluxDB 客户端，实现传感器时序数据的存储与查询，支持 MySQL 为主数据库，预留 SQLite、Oracle 兼容扩展，通过配置文件切换数据库类型。

- **基础设施层**：包含 MQTT 客户端（设备消息接入）、Redis（缓存扩展预留）、Kafka（消息队列扩展预留）、InfluxDB（时序数据存储），负责底层通信、缓存、消息分发、时序数据存储等基础能力。

## 1\.3 技术选型

|技术组件|版本选型|用途说明|
|---|---|---|
|SpringBoot|3\.2\.x|项目核心框架，提供自动配置、依赖注入等基础能力|
|JDK|21|项目运行环境，支持最新 LTS 版本，保障性能与稳定性|
|MyBatis\-Plus|3\.5\.5|数据库 ORM 框架，简化 MySQL 相关 CRUD 操作，支持多数据库兼容|
|MQTT Client|1\.2\.5（Eclipse Paho）|实现与 EMQX  Broker 的通信，接收设备上报消息、下发控制指令|
|MySQL|8\.0|主数据库，存储设备元数据、设备指令等业务数据|
|InfluxDB|2\.7\.x|时序数据库，存储设备上报的传感器数据（温度、湿度、气压等）|
|Redis|预留（6\.x 版本）|后期扩展：缓存设备在线状态、最新数据、热点查询数据|
|Kafka|预留（3\.x 版本）|后期扩展：消息削峰、解耦，处理设备高并发上报消息|
|Fastjson2|2\.0\.32|JSON 解析与序列化，处理设备上报的 JSON 格式数据|
|InfluxDB Client|6\.12\.0|InfluxDB 客户端，实现传感器时序数据的写入与查询|
|Swagger（io\.swagger\.v3）|3\.0\.0|接口文档生成与调试工具，自动生成 API 文档，支持在线接口调试|

# 二、数据库设计

## 2\.1 设计原则

## 2\.2 核心表结构设计（MySQL）

- 遵循物联网数据规范，聚焦设备核心信息，不涉及角色权限相关表，简化设计。

- 数据分层存储：设备元数据、指令等业务数据存储于 MySQL，传感器时序数据存储于 InfluxDB，各司其职，提升查询与存储效率。

- MySQL 支持多数据库兼容，字段类型采用通用设计，避免数据库专属语法，后期可通过配置切换 SQLite、Oracle；InfluxDB 采用标准时序数据设计，适配物联网场景。

- 字段命名规范，采用下划线命名法，添加注释，便于维护与扩展；主键采用自增 ID，设备唯一标识使用 device\_id（字符串类型）。

### 2\.2\.1 设备信息表（iot\_device）

存储设备基础元数据，是设备管理的核心表，记录设备唯一标识、名称、型号、状态等信息，关联其他业务表。

索引：UNIQUE KEY uk\_device\_id \(device\_id\)，确保设备唯一标识不重复。

### 2\.2\.2 设备指令表（iot\_device\_command）

|字段名|数据类型|长度|是否主键|是否允许为空|默认值|注释|
|---|---|---|---|---|---|---|
|id|VARCHAR|64|是|否|无（通过业务逻辑生成唯一字符串主键）|主键 ID|
|device\_id|VARCHAR|64|否|否|无|设备唯一标识（与设备上报的 deviceId 一致）|
|device\_name|VARCHAR|100|否|是|无|设备名称|
|device\_model|VARCHAR|50|否|是|无|设备型号（如 ESP32\-S3）|
|firmware\_version|VARCHAR|32|否|是|无|固件版本号|
|location|VARCHAR|128|否|是|无|设备安装位置|
|status|TINYINT|1|否|否|1|设备状态（0：离线，1：在线，2：异常）|
|create\_time|DATETIME|\-|否|否|CURRENT\_TIMESTAMP|设备创建时间|
|update\_time|DATETIME|\-|否|否|CURRENT\_TIMESTAMP ON UPDATE CURRENT\_TIMESTAMP|设备信息更新时间|

存储后端下发给设备的控制指令，记录指令内容、状态、下发时间，支持指令追踪与重试，关联设备表的 device\_id。

索引：KEY idx\_device\_id \(device\_id\)，便于查询指定设备的所有指令。

|字段名|数据类型|长度|是否主键|是否允许为空|默认值|注释|
|---|---|---|---|---|---|---|
|id|VARCHAR|64|是|否|无（通过业务逻辑生成唯一字符串主键）|主键 ID|
|device\_id|VARCHAR|64|否|否|无|关联设备表的 device\_id|
|command|VARCHAR|255|否|否|无|指令内容（如："set\_temp:25"、"restart"）|
|status|TINYINT|1|否|否|0|指令状态（0：待下发，1：已下发，2：执行成功，3：执行失败）|
|create\_time|DATETIME|\-|否|否|CURRENT\_TIMESTAMP|指令创建时间|
|update\_time|DATETIME|\-|否|否|CURRENT\_TIMESTAMP ON UPDATE CURRENT\_TIMESTAMP|指令更新时间|

索引：KEY idx\_device\_id \(device\_id\)，便于查询指定设备的所有指令。

## 2\.3 数据库兼容说明

## 2\.3 InfluxDB 时序数据设计

传感器数据（温度、湿度、气压等）属于时序数据，采用 InfluxDB 存储，设计如下：

- **Organization（组织）**：iot\_demo（可自定义，用于隔离不同项目数据）

- **Bucket（存储桶）**：sensor\_data（存储传感器时序数据，可设置数据保留策略）

- **Measurement（测量值）**：sensor（相当于 MySQL 的表，用于存储传感器数据）

- **Tag（标签）**：device\_id（设备唯一标识，用于区分不同设备，支持按设备筛选数据）

- **Field（字段）**：temp（温度，DECIMAL 类型）、humi（湿度，DECIMAL 类型）、press（气压，BIGINT 类型），存储具体传感器数值

- **Time（时间戳）**：report\_time（数据上报时间，采用设备上报的 ts 或系统当前时间，作为时序数据的主键）

示例数据格式（Line Protocol）：

```plain text
sensor,device_id=esp32s3_001 temp=24.6,humi=58.2,press=101325i 1754208888000000000
```

## 2\.4 数据库兼容说明

- MySQL 部分：当前基于 MySQL 8\.0 设计，主键采用 VARCHAR 字符串类型（适配企业常用设计），其他字段类型采用通用类型（如 VARCHAR、DECIMAL），避免使用 MySQL 专属函数，后期可通过 MyBatis\-Plus 的主键策略适配其他数据库。

- 后期兼容 SQLite 时，需调整 MySQL 表字段类型（如 DATETIME 适配 SQLite 的 TEXT 类型），通过 MyBatis\-Plus 的多数据库配置实现切换，无需修改核心业务代码。

- 兼容 Oracle 时，需调整 MySQL 表主键生成方式（使用序列替代自增），字段长度适配 Oracle 限制，同样通过配置文件实现，不影响业务逻辑。

- InfluxDB 部分：采用标准时序数据设计，不依赖特定数据库版本，后期可根据需求升级 InfluxDB 版本，无需调整数据结构。

- 当前基于 MySQL 8\.0 设计，主键采用 VARCHAR 字符串类型，其他字段类型采用通用类型（如 VARCHAR、DECIMAL），避免使用 MySQL 专属函数，后期可通过 MyBatis\-Plus 的主键策略适配其他数据库。

- 后期兼容 SQLite 时，需调整字段类型（如 DATETIME 适配 SQLite 的 TEXT 类型），通过 MyBatis\-Plus 的多数据库配置实现切换，无需修改核心业务代码。

- 兼容 Oracle 时，需调整主键生成方式（使用序列替代自增），字段长度适配 Oracle 限制，同样通过配置文件实现，不影响业务逻辑。

# 三、核心业务模块设计

## 3\.1 设备管理模块

设备管理模块是系统核心基础模块，负责设备全生命周期管理，涵盖设备注册、状态监控、信息维护、查询筛选等核心功能，对接 MySQL 设备表，通过 API 层对外提供标准化接口，支撑前端与第三方平台的设备管理需求。

- 设备注册与录入：支持两种注册模式，一是自动注册，设备通过 MQTT 上报 device\_id 后，系统自动校验并写入 MySQL 设备表，生成基础设备信息；二是手动录入，通过 API 接口批量或单个录入设备名称、型号、位置等详细信息，完善设备档案。

- 设备状态管理：实时监控设备在线/离线/异常状态，通过 MQTT 心跳包、连接状态判断设备存活情况，同步更新 MySQL 设备表 status 字段；支持状态变更日志记录，便于追溯设备状态变化。

- 设备信息维护：提供设备信息修改、删除功能，支持批量更新设备型号、固件版本、安装位置等信息；删除操作采用逻辑删除，保留历史数据，避免数据丢失。

- 设备查询与筛选：提供多维度查询接口，支持按设备 ID、名称、型号、状态等条件筛选，支持分页查询，返回标准化 DTO 数据，适配前端列表展示与数据统计需求。

## 3\.2 MQTT 消息处理模块

MQTT 消息处理模块负责设备与系统之间的消息交互，涵盖设备上报消息接收、解析、处理，以及系统控制指令下发，基于 Eclipse Paho MQTT Client 实现，对接 EMQX Broker，保障消息传输的稳定性与可靠性。

- 消息接收与订阅：配置 MQTT 客户端，订阅设备上报主题（如 device/data/upload），实时接收设备发送的传感器数据、心跳包、注册请求等 JSON 格式报文，支持多主题订阅，适配不同设备的消息上报场景。

- 消息解析与封装：通过 Fastjson2 解析上报的 JSON 报文，提取 device\_id、传感器数据（温度、湿度等）、上报时间等关键信息，校验报文格式与数据合法性，封装为 SensorData 实体与对应 DTO，便于后续业务处理。

- 指令下发与追踪：提供控制指令下发接口，支持向指定设备下发重启、参数设置等指令，指令内容封装为 MQTT 消息发送至设备专属主题；同步更新 MySQL 设备指令表，记录指令下发状态、执行结果，支持指令重试与追溯。

- 消息异常处理：针对消息接收失败、解析失败、下发失败等场景，进行异常捕获与日志记录，支持异常消息重试机制，避免消息丢失，保障系统与设备的正常通信。

## 3\.3 数据存储与查询模块

数据存储与查询模块负责系统各类数据的持久化存储与高效查询，实现 MySQL 业务数据与 InfluxDB 时序数据的分层管理，兼顾数据存储可靠性与查询效率，支撑设备数据追溯与统计需求。

- 传感器数据存储：将解析后的传感器时序数据（温度、湿度、气压等）封装为 InfluxDB Point，写入 InfluxDB 的 sensor 测量值中，关联设备 ID 与上报时间，设置数据保留策略，清理过期数据，优化存储性能。

- 业务数据同步：设备信息、指令状态等业务数据发生变更时，同步更新 MySQL 对应表，通过事务保障数据一致性；设备注册、状态变更等操作完成后，同步记录操作日志，便于数据追溯。

- 多维度数据查询：支持两类核心查询，一是设备基础信息查询，从 MySQL 中获取设备档案、指令记录等数据；二是传感器历史数据查询，从 InfluxDB 中按设备 ID、时间范围筛选历史数据，支持分页、排序，返回标准化 DTO 数据。

- 数据备份与恢复：预留数据备份接口，支持 MySQL 业务数据定时备份、InfluxDB 时序数据批量导出；支持数据恢复功能，应对数据丢失场景，保障系统数据安全。

# 四、后期扩展规划（Redis、Kafka）

## 4\.1 Redis 扩展思路

- 设备在线状态缓存：将设备在线状态存储在 Redis 中，设置过期时间（如60秒），每次接收设备上报消息时刷新过期时间，替代频繁查询 MySQL，提升状态查询效率。

- 最新传感器数据缓存：缓存每个设备的最新一条传感器数据，前端查询最新数据时直接从 Redis 获取，减少 InfluxDB 查询压力。

后期引入 Redis 主要用于缓存热点数据、提升查询效率，具体应用场景如下：

- 热点设备信息缓存：缓存高频访问的设备基础信息，避免重复查询 MySQL。

实现方式：通过 Spring Data Redis 集成 Redis，配置 Redis 连接工厂，封装缓存工具类，在业务层按需调用缓存接口，实现数据的存入与读取。

- 设备注册：支持设备自动注册（通过 MQTT 上报的 device\_id 自动写入 MySQL 设备表），也支持手动录入设备信息。

- 设备状态管理：实时更新设备在线/离线状态，通过 MQTT 连接状态、心跳包判断设备状态，同步更新 MySQL 设备表 status 字段。

- 设备信息查询：提供设备列表、设备详情查询接口，支持按设备 ID、名称、型号筛选，优先实现基础查询功能，通过 API 层对外提供服务。

## 4\.2 Kafka 扩展思路

后期引入 Kafka 主要用于消息削峰、解耦，应对设备高并发上报场景，提升系统稳定性，具体应用场景如下：

- 消息接收：通过 Eclipse Paho MQTT Client 连接 EMQX Broker，订阅设备上报主题（如 device/data/upload），接收传感器数据 JSON 报文。

- 消息解析：使用 Fastjson2 解析 JSON 报文，提取 device\_id、温度、湿度、气压、上报时间等信息，封装为 SensorData 实体与对应 DTO。

- 消息下发：提供指令下发接口，通过 MQTT 向指定设备下发控制指令，更新 MySQL 设备指令表状态。

实现方式：通过 Spring Kafka 集成 Kafka，配置 Kafka 生产者与消费者，EMQX Broker 将设备上报消息桥接至 Kafka 主题（如 iot\_device\_raw），SpringBoot 消费者监听该主题，处理消息并完成数据存储等业务逻辑。

- 传感器数据存储：解析后的传感器数据封装为 InfluxDB Point，写入 InfluxDB 的 sensor 测量值中，关联设备 ID，保留历史时序数据。

- 设备数据同步：设备信息更新、指令状态变更时，同步更新 MySQL 对应表，保证数据一致性。

- 设备数据查询：支持查询设备基础信息（从 MySQL 获取）、设备历史传感器数据（从 InfluxDB 获取），通过 API 层对外提供查询接口，返回标准化 DTO 数据。

- 传感器数据存储：解析后的传感器数据封装为 InfluxDB Point，写入 InfluxDB 的 sensor 测量值中，关联设备 ID，保留历史时序数据。

- 设备数据同步：设备信息更新、指令状态变更时，同步更新 MySQL 对应表，保证数据一致性。

- 设备数据查询：支持查询设备基础信息（从 MySQL 获取）、设备历史传感器数据（从 InfluxDB 获取），通过 API 层对外提供查询接口，返回标准化 DTO 数据。

# 五、全局异常捕获与切面设计

## 5\.1 全局异常捕获处理

为规范系统异常处理、避免异常信息直接暴露给前端，采用 SpringBoot 全局异常捕获机制，统一处理各类异常，返回标准化响应结果，提升系统稳定性与用户体验。

### 5\.1\.1 设计思路

通过`@RestControllerAdvice`注解定义全局异常处理类，拦截所有控制器抛出的异常，区分业务异常与系统异常，封装统一响应体（包含状态码、提示信息、数据等），同时记录异常日志，便于排查问题。

### 5\.1\.2 核心实现

- 自定义业务异常类：继承`RuntimeException`，包含异常状态码、提示信息，用于业务逻辑中主动抛出异常（如设备不存在、参数校验失败等）。

- 全局异常处理类：通过`@ExceptionHandler`注解捕获各类异常，包括自定义业务异常、参数校验异常、系统未知异常等，统一封装响应结果。

- 日志记录：捕获异常时，通过日志框架记录异常堆栈信息，便于开发人员定位问题。

## 5\.2 API 层切面设计

采用 Aspect 切面技术，针对 API 层 URL 进行切面拦截，统一打印接口入参、出参，便于接口调试、日志追溯，同时不侵入业务代码，符合“低耦合”设计原则。

### 5\.2\.1 设计思路

基于 Spring AOP 实现，定义切面类，通过切点表达式匹配 API 层所有接口 URL，在接口执行前后拦截，记录请求 URL、请求方式、入参、出参、执行耗时等信息，打印至日志，同时可扩展接口权限校验、限流等功能。

### 5\.2\.2 核心实现

- 切面类：添加`@Aspect`、`@Component`注解，定义切点表达式（如`execution(* com.dboat.iot.api.*.*(..))`），匹配 API 层所有接口方法。

- 通知类型：采用`@Around`环绕通知，在接口方法执行前记录入参，执行后记录出参，同时计算接口执行耗时。

- 日志格式：统一日志格式，包含时间戳、请求 URL、请求方式、入参、出参、执行耗时，便于日志分析与排查。

# 六、项目目录结构

## 6\.1 DTO 命名规范

|类型|命名规则|示例|包路径|
|---|---|---|---|
|请求参数 DTO|`xxxReqDTO`|`DeviceCreateReqDTO`、`SensorDataQueryReqDTO`|`com.dboat.iot.dto.request`|
|响应数据 DTO|`xxxRespDTO`|`DeviceRespDTO`、`CommandRespDTO`|`com.dboat.iot.dto.response`|

**说明：**
- 请求 DTO 统一以 `ReqDTO` 结尾，放在 `dto/request/` 子包下。
- 响应 DTO 统一以 `RespDTO` 结尾，放在 `dto/response/` 子包下。
- 所有 DTO 类均不使用 `Request`/`Response` 后缀，以避免与 Spring MVC 的 `HttpServletRequest`/`HttpServletResponse` 产生混淆。

```plain text
src
├── main
│   ├── java
│   │   └── com
│   │       └── dboat
│   │           └── iot
│   │               ├── DeviceApplication.java  // 项目启动类
│   │               ├── api  // API 层：对外提供 HTTP 接口
│   │               │   ├── DeviceController.java  // 设备管理接口（查询、注册等）
│   │               │   ├── SensorDataController.java  // 传感器数据查询接口
│   │               │   └── DeviceCommandController.java  // 指令下发与查询接口
│   │               ├── dto  // DTO 层：数据传输对象
│   │               │   ├── request  // 请求参数 DTO（统一 xxxReqDTO 命名）
│   │               │   │   ├── DeviceCreateReqDTO.java  // 设备创建请求
│   │               │   │   ├── DeviceUpdateReqDTO.java  // 设备更新请求
│   │               │   │   ├── CommandSendReqDTO.java  // 指令发送请求
│   │               │   │   └── SensorDataQueryReqDTO.java  // 传感器数据查询请求
│   │               │   └── response  // 响应数据 DTO（统一 xxxRespDTO 命名）
│   │               │       ├── DeviceRespDTO.java  // 设备信息响应
│   │               │       ├── SensorDataRespDTO.java  // 传感器数据响应
│   │               │       ├── CommandRespDTO.java  // 指令响应
│   │               │       └── Result.java  // 统一响应包装类
│   │               ├── config  // 配置类
│   │               │   ├── MyBatisPlusConfig.java  // MyBatis-Plus 配置
│   │               │   ├── MyBatisMetaObjectHandler.java  // 自动填充 createTime/updateTime
│   │               │   ├── MqttConfig.java  // MQTT 配置
│   │               │   ├── InfluxDBConfig.java  // InfluxDB 配置
│   │               │   └── SwaggerConfig.java  // Swagger 接口文档配置
│   │               ├── entity  // 实体类（对应数据库表）
│   │               │   ├── Device.java  // 设备实体（MySQL）
│   │               │   ├── DeviceCommand.java  // 设备指令实体（MySQL）
│   │               │   └── SensorData.java  // 传感器数据实体（InfluxDB）
│   │               ├── mapper  // MyBatis-Plus 映射接口（仅 MySQL 相关）
│   │               │   ├── DeviceMapper.java
│   │               │   └── DeviceCommandMapper.java
│   │               ├── service  // 业务接口
│   │               │   ├── DeviceService.java
│   │               │   ├── SensorDataService.java
│   │               │   └── DeviceCommandService.java
│   │               ├── service.impl  // 业务实现类
│   │               │   ├── DeviceServiceImpl.java
│   │               │   ├── SensorDataServiceImpl.java  // 处理 InfluxDB 数据读写
│   │               │   └── DeviceCommandServiceImpl.java
│   │               ├── mqtt  // MQTT 相关处理
│   │               │   ├── MqttClientManager.java  // MQTT 客户端管理
│   │               │   └── MqttMessageHandler.java  // MQTT 消息处理器
│   │               ├── aspect  // 切面类
│   │               │   └── ApiLogAspect.java  // API 层日志切面，打印入参、出参
│   │               ├── exception  // 异常处理
│   │               │   ├── BusinessException.java  // 自定义业务异常类
│   │               │   └── GlobalExceptionHandler.java  // 全局异常处理类
│   │               └── utils  // 工具类
│   │                   ├── JsonUtils.java  // JSON 解析工具
│   │                   └── InfluxDBUtils.java  // InfluxDB 操作工具
│   ├── resources
│   │   ├── application.yml  // 项目配置文件（含 InfluxDB、MQTT、MySQL 配置）
│   │   ├── mapper  // MyBatis 映射文件（仅 MySQL 相关）
│   │   │   ├── DeviceMapper.xml
│   │   │   └── DeviceCommandMapper.xml
│   │   └── sql  // 数据库脚本
│   │       └── iot_device.sql  // MySQL 数据库表创建脚本
└── test
    └── java
        └── com
            └── dboat
                └── iot
                    └── DeviceApplicationTests.java  // 测试类
```

## 6\.2 Swagger 接口文档与调试说明

### 6\.2\.1 配置说明

新增`SwaggerConfig.java`配置类，基于 io\.swagger\.v3 实现接口文档自动生成，配置文档标题、描述、版本、扫描包路径等信息，指定 API 层接口所在包（com\.dboat\.iot\.api），确保仅扫描业务接口，过滤冗余接口。

### 6\.2\.2 接口调试方式

项目启动后，通过 Swagger UI 在线页面进行接口调试，访问路径为`/swagger-ui.html`（默认端口结合项目配置，如 http://localhost:8080/swagger\-ui\.html），可实现以下功能：

- 查看所有 API 接口详情，包括请求方式、请求参数、响应格式、接口说明等，自动同步 API 层接口注解信息。

- 在线调试接口，支持手动输入请求参数，发起 HTTP 请求，实时查看接口返回结果，便于接口开发与测试阶段的问题排查。

- 导出接口文档，支持 JSON、YAML 格式，便于前后端开发人员对接，规范接口交互标准。

### 6\.2\.3 注意事项

- 生产环境建议关闭 Swagger 接口文档，通过配置文件（application\.yml）设置开关，避免接口信息泄露。

- 接口调试时，需确保项目正常启动，数据库、MQTT、InfluxDB 等依赖服务已就绪，避免因服务异常导致接口调试失败。

## 6\.1 SensorData 相关设计说明（实体类、Mapper 存在必要性）

项目目录结构中存在 SensorData 实体类、SensorDataService 等相关模块，虽未在 MySQL 表结构中体现（因传感器数据存储于 InfluxDB），但该设计具备明确必要性，具体原因如下：

- **适配 InfluxDB 数据操作需求**：SensorData 实体类并非对应 MySQL 表，而是映射 InfluxDB 的 measurement（sensor），用于封装传感器时序数据（温度、湿度、气压等），通过 InfluxDB 客户端实现数据的写入与查询，是 InfluxDB 数据操作的核心载体，若无该实体类，无法规范数据封装与传输。

- **规范业务逻辑分层**：SensorDataService 及实现类，专门处理传感器数据的存储、查询业务，与设备管理、指令管理等业务模块分离，符合“单一职责原则”，避免业务逻辑混杂，便于后期维护与扩展（如新增传感器数据统计、告警等功能）。

- **对接 API 层数据交互**：API 层的 SensorDataController 提供传感器数据查询接口，需通过 SensorData 实体类封装查询结果，再转换为 DTO 返回给前端，实现数据的标准化传输，同时避免直接暴露 InfluxDB 底层数据结构。

- **预留扩展空间**：后期若需对传感器数据进行复杂处理（如数据清洗、批量查询、历史数据导出等），可直接在 SensorDataService 中扩展相关方法，无需重构整体架构，提升系统扩展性。

补充说明：目录中 SensorDataMapper 已移除（原设计冗余），因 InfluxDB 数据操作无需 MyBatis\-Plus 映射接口，通过 InfluxDB 客户端工具类（InfluxDBUtils）即可实现数据读写，贴合 InfluxDB 时序数据库的操作特性。

> （注：部分内容可能由 AI 生成）
