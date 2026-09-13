# 物联网设备管理系统 SpringBoot 项目设计文档

# 一、项目整体架构设计

## 1\.1 架构概述

本项目基于 SpringBoot 3\.x \+ JDK21 构建，聚焦物联网设备接入、数据采集与基础管理，采用分层架构设计，核心遵循“高内聚、低耦合”原则，预留 Redis、Kafka 扩展接口，支持后期平滑升级。整体架构分为五层，新增 API 层与 DTO 层，各层职责清晰，互不干扰。

## 1\.2 核心架构分层

- **API 层**：提供统一 POST 风格 HTTP 接口，所有接口均使用 POST 请求，入参统一通过 DTO 接收，负责请求接收、参数校验、响应封装，对接前端系统与第三方平台，优先支持设备数据查询相关接口。

- **DTO 层**：数据传输对象层，封装接口请求与响应数据，实现实体类与外部数据的解耦，避免敏感字段泄露，规范数据传输格式。请求 DTO 统一继承 `BaseReqDTO` 基类，定义 requestId、timestamp、sign 等公共参数。

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
|请求基类 DTO|`BaseReqDTO`|`BaseReqDTO`（所有请求 DTO 的父类）|`com.dboat.iot.dto.request`|
|响应数据 DTO|`xxxRespDTO`|`DeviceRespDTO`、`CommandRespDTO`|`com.dboat.iot.dto.response`|

**说明：**
- 请求 DTO 统一以 `ReqDTO` 结尾，放在 `dto/request/` 子包下，全部继承 `BaseReqDTO` 基类。
- `BaseReqDTO` 定义所有请求的公共参数（如 `requestId`、`timestamp`、`sign`），各子 DTO 通过继承复用，避免重复定义。
- 响应 DTO 统一以 `RespDTO` 结尾，放在 `dto/response/` 子包下。
- 所有 DTO 类均不使用 `Request`/`Response` 后缀，以避免与 Spring MVC 的 `HttpServletRequest`/`HttpServletResponse` 产生混淆。
- 所有 API 接口统一使用 POST 请求，入参通过 `@RequestBody` 接收 DTO，路径参数与查询参数均封装在 DTO 中传递。

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
│   │               │   ├── request  // 请求参数 DTO（统一 xxxReqDTO 命名，继承 BaseReqDTO）
│   │               │   │   ├── BaseReqDTO.java  // 请求基类（requestId、timestamp、sign 等公共参数）
│   │               │   │   ├── DeviceCreateReqDTO.java  // 设备创建请求
│   │               │   │   ├── DeviceUpdateReqDTO.java  // 设备更新请求
│   │               │   │   ├── DeviceDeleteReqDTO.java  // 设备删除请求
│   │               │   │   ├── DeviceGetByIdReqDTO.java  // 按 ID 查询设备请求
│   │               │   │   ├── DeviceGetByDeviceIdReqDTO.java  // 按设备 ID 查询请求
│   │               │   │   ├── DeviceListReqDTO.java  // 设备列表查询请求
│   │               │   │   ├── CommandSendReqDTO.java  // 指令发送请求
│   │               │   │   ├── CommandQueryReqDTO.java  // 按设备查询指令请求
│   │               │   │   ├── CommandGetByIdReqDTO.java  // 按 ID 查询指令请求
│   │               │   │   └── SensorDataQueryReqDTO.java  // 传感器数据查询请求
│   │               │   │   └── SensorDataLatestReqDTO.java  // 传感器最新数据查询请求
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
│   │               │   ├── TelemetryDataService.java
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

项目目录结构中存在 SensorData 实体类、TelemetryDataService 等相关模块，虽未在 MySQL 表结构中体现（因传感器数据存储于 InfluxDB），但该设计具备明确必要性，具体原因如下：

- **适配 InfluxDB 数据操作需求**：SensorData 实体类并非对应 MySQL 表，而是映射 InfluxDB 的 measurement（sensor），用于封装传感器时序数据（温度、湿度、气压等），通过 InfluxDB 客户端实现数据的写入与查询，是 InfluxDB 数据操作的核心载体，若无该实体类，无法规范数据封装与传输。

- **规范业务逻辑分层**：TelemetryDataService 及实现类，专门处理传感器数据的存储、查询业务，与设备管理、指令管理等业务模块分离，符合“单一职责原则”，避免业务逻辑混杂，便于后期维护与扩展（如新增传感器数据统计、告警等功能）。

- **对接 API 层数据交互**：API 层的 SensorDataController 提供传感器数据查询接口，需通过 SensorData 实体类封装查询结果，再转换为 DTO 返回给前端，实现数据的标准化传输，同时避免直接暴露 InfluxDB 底层数据结构。

- **预留扩展空间**：后期若需对传感器数据进行复杂处理（如数据清洗、批量查询、历史数据导出等），可直接在 TelemetryDataService 中扩展相关方法，无需重构整体架构，提升系统扩展性。

补充说明：目录中 SensorDataMapper 已移除（原设计冗余），因 InfluxDB 数据操作无需 MyBatis\-Plus 映射接口，通过 InfluxDB 客户端工具类（InfluxDBUtils）即可实现数据读写，贴合 InfluxDB 时序数据库的操作特性。

# 七、开发规范与基础配置

## 7\.1 多环境配置规范

项目采用 Spring Profiles 机制实现多环境配置管理，通过 `application.yml` 作为主配置文件，`application-{profile}.yml` 作为环境专属配置文件，实现环境隔离。

### 7\.1\.1 配置文件结构

| 配置文件 | 用途 | 说明 |
|---|---|---|
| `application.yml` | 主配置文件 | 定义通用配置项及当前激活的环境（`spring.profiles.active`） |
| `application-local.yml` | 本地开发环境 | 开发者本机运行，连接本地中间件（localhost），敏感信息使用脱敏占位值 |
| `application-local-vm.yml` | 本地联调环境 | 连接局域网/测试服务器中间件，用于联调与功能验证 |

### 7\.1\.2 环境切换方式

通过主配置文件 `application.yml` 中的 `spring.profiles.active` 属性切换激活环境：

```yaml
spring:
  profiles:
    active: local-vm   # 可选值：local、local-vm
```

### 7\.1\.3 环境配置规范

- **敏感信息隔离**：`application-local.yml` 和 `application-local-vm.yml` 包含数据库密码、Token 等敏感信息，已在 `.gitignore` 中配置忽略，禁止提交至代码仓库。
- **功能总开关**：`application-local.yml` 提供 `feature.mqtt-enable`、`feature.influxdb-enable` 开关配置，本地开发时可按需关闭 MQTT、InfluxDB 等中间件连接，降低本地启动依赖。
- **配置项命名规范**：所有自定义配置项统一使用小写加连字符（kebab-case）格式，如 `broker-url`、`keep-alive-interval`，与 SpringBoot 官方风格保持一致。

## 7\.2 .gitignore 规范

项目 `.gitignore` 按分类分区管理，覆盖编译产物、IDE 文件、操作系统文件、日志、敏感配置等场景，具体分区如下：

| 分区 | 忽略内容 | 说明 |
|---|---|---|
| 编译产物 | `*.class`、`*.jar`、`target/` 等 | 忽略所有编译生成的二进制文件与构建输出目录 |
| Maven/Gradle 输出 | `target/`、`.gradle/`、`build/` | 忽略构建工具输出，保留 Maven Wrapper JAR |
| IDE 文件 | `.idea/`、`*.iml`、`.classpath` 等 | 忽略 IntelliJ IDEA、Eclipse、NetBeans、VS Code 等 IDE 专属文件 |
| 操作系统文件 | `.DS_Store`、`Thumbs.db`、`Desktop.ini` 等 | 忽略 macOS、Windows 系统自动生成的元数据文件 |
| 日志文件 | `*.log`、`logs/` | 忽略运行时产生的日志文件与日志目录 |
| 敏感配置 | `application-local.yml`、`application-local-vm.yml`、`*.env` 等 | 忽略包含密码、Token 等敏感信息的环境配置文件，防止泄露 |
| Spring Boot | `*.pid`、`spring-boot-devtools.jar` | 忽略 Spring Boot 运行时产生的进程文件与热部署 JAR |
| 临时文件 | `*.bak`、`*.tmp`、`*.temp`、`hs_err_pid*` 等 | 忽略各类临时文件与 JVM 崩溃日志 |

**规范要求：**

- 新增配置文件若包含敏感信息（如密码、Token、密钥等），必须同步更新 `.gitignore` 添加对应忽略规则。
- 禁止使用 `git add -f` 强制提交已忽略的文件，特殊情况需经团队评审确认。

## 7\.3 跨域配置

项目通过 `WebConfig.java` 配置类注册全局 `CorsFilter`，统一处理跨域请求，支持前端开发调试与第三方平台对接。

### 7\.3\.1 配置说明

- **实现方式**：通过 `@Configuration` 配置类注册 `CorsFilter` Bean，基于 Spring 标准的 `UrlBasedCorsConfigurationSource` 实现，全局生效。
- **允许来源**：`addAllowedOriginPattern("*")`，允许所有来源跨域访问（开发阶段），生产环境建议限制为具体域名。
- **允许方法**：`addAllowedMethod("*")`，支持 GET、POST、PUT、DELETE 等所有 HTTP 方法。
- **允许请求头**：`addAllowedHeader("*")`，允许所有请求头。
- **凭证支持**：`setAllowCredentials(true)`，允许跨域携带 Cookie 等凭证信息。
- **预检缓存**：`setMaxAge(3600L)`，预检请求（OPTIONS）结果缓存 3600 秒（1 小时），减少预检请求频次。

### 7\.3\.2 注意事项

- 当前配置为开发阶段全开放模式，生产环境部署时需将 `addAllowedOriginPattern("*")` 修改为具体的前端域名，避免安全风险。
- 跨域配置全局生效（`/**`），覆盖所有 API 接口路径。

## 7\.4 全局异常处理规范

项目通过 `GlobalExceptionHandler`（`@RestControllerAdvice`）与自定义 `BusinessException` 配合，实现全局异常统一捕获与标准化响应，避免异常堆栈信息直接暴露给前端。

### 7\.4\.1 统一响应格式

所有接口返回统一的 `Result<T>` 包装结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `int` | 状态码，200 表示成功，其他为异常码 |
| `message` | `String` | 提示信息 |
| `data` | `T` | 响应数据（异常时为 null） |

### 7\.4\.2 异常分类处理

| 异常类型 | HTTP 状态码 | 响应 code | 处理方式 |
|---|---|---|---|
| `BusinessException`（自定义业务异常） | 200 | 异常自定义 code（默认 500） | 记录 error 日志，返回业务错误信息 |
| `MethodArgumentNotValidException`（参数校验异常） | 400 | 400 | 提取字段校验错误信息拼接，记录 error 日志 |
| `BindException`（参数绑定异常） | 400 | 400 | 提取字段绑定错误信息拼接，记录 error 日志 |
| `Exception`（未知系统异常） | 500 | 500 | 记录完整异常堆栈日志，返回通用错误提示"Internal server error" |

### 7\.4\.3 自定义业务异常

`BusinessException` 继承 `RuntimeException`，包含 `code`（异常码）和 `message`（错误信息）两个属性，支持两种构造方式：

- `BusinessException(String message)`：默认 code 为 500，用于一般业务异常。
- `BusinessException(int code, String message)`：自定义异常码，用于需要精确标识异常类型的场景。

### 7\.4\.4 使用规范

- 业务逻辑中需要主动抛出异常时，统一使用 `BusinessException`，禁止直接抛出 `RuntimeException`。
- 全局异常处理器捕获异常时，均通过 `log.error()` 记录异常日志，业务异常记录异常消息，系统异常记录完整堆栈。
- 前端根据响应 `code` 判断请求是否成功，`message` 可直接用于用户提示。

## 7\.5 日志格式规范

项目基于 Logback 实现日志管理，通过 `logback-spring.xml` 配置文件定义日志输出格式，区分控制台与文件两种输出通道，分别适配开发调试与生产运维场景。

### 7\.5\.1 日志输出通道

| 通道 | Appender | 输出目标 | 特点 |
|---|---|---|---|
| 控制台 | `CONSOLE` | 标准输出（stdout） | 支持 ANSI 彩色高亮，便于开发阶段快速定位日志级别 |
| 文件 | `FILE` | `logs/home-pilot.log` | 纯文本格式，按天+大小切割，便于生产环境日志采集与归档 |

### 7\.5\.2 日志格式定义

**控制台日志格式（彩色）：**

```plain text
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %highlight(%-5level) %cyan(%logger{40}:%L) - %msg%n
```

示例输出：

```plain text
2026-08-08 10:30:15.123 [http-nio-8080-exec-1] INFO  com.dboat.iot.api.DeviceController:45 - [API] GET /api/device/list | Response: {...} | Elapsed: 32ms
```

**文件日志格式（纯文本）：**

```plain text
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{40}:%L - %msg%n
```

示例输出：

```plain text
2026-08-08 10:30:15.123 [http-nio-8080-exec-1] INFO  com.dboat.iot.api.DeviceController:45 - [API] GET /api/device/list | Response: {...} | Elapsed: 32ms
```

### 7\.5\.3 格式字段说明

| 字段 | 说明 |
|---|---|
| `%d{yyyy-MM-dd HH:mm:ss.SSS}` | 时间戳，精确到毫秒 |
| `[%thread]` | 线程名称 |
| `%-5level` | 日志级别，左对齐占 5 字符（控制台带颜色高亮） |
| `%logger{40}:%L` | 类名（最长 40 字符缩写）加行号 |
| `%msg` | 日志消息内容 |
| `%n` | 换行符 |

### 7\.5\.4 日志文件滚动策略

| 配置项 | 值 | 说明 |
|---|---|---|
| 当前日志文件 | `logs/home-pilot.log` | 正在写入的日志文件 |
| 滚动策略 | 按天 + 按大小 | 文件名格式：`home-pilot.yyyy-MM-dd.{序号}.log` |
| 单文件大小上限 | 100MB | 超过后自动切割生成新文件 |
| 历史保留天数 | 5 天 | 超过 5 天的日志自动清理 |
| 总大小上限 | 2GB | 所有日志文件总大小不超过 2GB |

### 7\.5\.5 第三方包日志降噪

为避免第三方库输出大量冗余日志干扰业务日志，对以下包进行日志级别控制：

| 包名 | 日志级别 | 说明 |
|---|---|---|
| `org.apache.tomcat` | WARN | 降低 Tomcat 容器日志输出 |
| `io.netty` | WARN | 降低 Netty 网络框架日志输出 |
| `com.influxdb` | INFO | InfluxDB 客户端日志，保留关键操作信息 |
| `org.eclipse.paho` | INFO | MQTT 客户端日志，保留连接与消息关键信息 |
| `org.springframework` | INFO | Spring 框架日志，保留启动与配置关键信息 |

## 7\.6 InfluxDB 日志打印规范

项目通过 `InfluxDBConfig` 配置类集成 OkHttp `HttpLoggingInterceptor`，实现 InfluxDB 操作（写入、查询）的 HTTP 请求日志输出，便于开发阶段调试与问题排查。

### 7\.6\.1 日志输出机制

- **实现方式**：在创建 `InfluxDBClient` 时，通过自定义 `OkHttpClient` 添加 `HttpLoggingInterceptor` 拦截器，拦截所有 InfluxDB HTTP 请求并输出日志。
- **日志前缀**：所有 InfluxDB HTTP 日志统一以 `[InfluxDB Http]` 为前缀，便于日志检索与过滤。
- **日志输出通道**：通过 SLF4J 的 `log.info()` 输出，纳入项目统一日志管理。

### 7\.6\.2 日志级别配置

通过 `application-{profile}.yml` 中的 `influxdb.log-level` 配置项控制日志详细程度：

| 日志级别 | 输出内容 | 适用场景 |
|---|---|---|
| `NONE` | 不输出任何日志 | 生产环境（性能优先） |
| `BASIC` | 仅输出请求方法、URL、状态码、耗时 | 生产环境（基础监控） |
| `HEADERS` | 输出请求头、响应头信息 | 调试阶段（排查认证等问题） |
| `BODY` | 输出完整请求体与响应体（含 Flux 查询脚本） | 开发环境（完整调试） |

### 7\.6\.3 环境配置建议

| 环境 | log-level 配置 | 说明 |
|---|---|---|
| 本地开发（local） | `BODY` | 输出完整 Flux 查询脚本与写入数据，便于调试 |
| 本地联调（local-vm） | `BODY` | 联调阶段输出完整日志，便于排查数据问题 |
| 生产环境 | `BASIC` 或 `NONE` | 减少日志量，保障系统性能 |

### 7\.6\.4 容错机制

- 日志级别配置错误时（如填写了非标准值），系统自动回退至 `BASIC` 级别，并通过 `log.warn()` 输出配置错误提示，避免因配置异常导致服务启动失败。

### 7\.6\.5 日志示例

开发环境（`BODY` 级别）典型日志输出：

```plain text
2026-08-08 10:30:15.200 [http-nio-8080-exec-2] INFO  c.d.i.config.InfluxDBConfig:32 - [InfluxDB Http] --> POST http://localhost:8086/api/v2/write?org=iot_demo&bucket=sensor_data
2026-08-08 10:30:15.205 [http-nio-8080-exec-2] INFO  c.d.i.config.InfluxDBConfig:32 - [InfluxDB Http] sensor,device_id=esp32s3_001 temp=24.6,humi=58.2,press=101325i 1754208888000000000
2026-08-08 10:30:15.210 [http-nio-8080-exec-2] INFO  c.d.i.config.InfluxDBConfig:32 - [InfluxDB Http] <-- 204 No Content (10ms)
```

> （注：部分内容可能由 AI 生成）
