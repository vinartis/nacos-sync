# Spec: nacos-sync-client

## Overview
定义 nacos-sync 与 Nacos 服务端交互的客户端封装、版本要求与生命周期管理。

## Requirements

### REQ-001: Nacos 客户端版本
- nacos-sync 必须使用 `nacos-client` 3.0.1 或更高版本
- 不再支持通过 nacos-client 1.x 连接 Nacos 服务端
- nacos-client 3.0.1 同时兼容 Nacos 2.x 与 3.x 服务端

### REQ-002: NamingService 实例管理
- `NamingService` 实例由 [NacosServerHolder](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/extension/holder/NacosServerHolder.java) 按 `clusterId` 缓存
- 实例创建使用 `NamingFactory.createNamingService(properties)` 公共工厂方法
- 实例销毁调用 `namingService.shutDown()` 释放资源

### REQ-003: 公共 API 使用约束
- 仅允许调用 `NamingService` 接口定义的公共方法
- 禁止通过反射访问 `NacosNamingService`、`NamingProxy` 等 nacos-client 内部实现类
- 禁止直接调用 Nacos v1/v2 HTTP API（`/nacos/v1/ns/...`、`/nacos/v2/ns/...`）

### REQ-004: 兼容的公共 API
以下 API 在 nacos-client 1.x / 2.x / 3.x 中保持兼容，nacos-sync 可放心使用：
- `getAllInstances(serviceName)` / `getAllInstances(serviceName, groupName)` 等 6 个重载
- `registerInstance(...)` 6 个重载
- `deregisterInstance(...)` 6 个重载
- `subscribe(...)` 6 个重载
- `unsubscribe(...)` 6 个重载
- `getServicesOfServer(pageNo, pageSize, groupName)` 4 个重载 — 用于分页列举服务

### REQ-005: 鉴权配置
- 使用 username/password 进行身份认证，通过 `Properties` 传入 `NamingFactory.createNamingService`
- 不再使用管理员账号或运维 SDK（nacos-maintainer-client）
- 鉴权信息复用现有 cluster 配置，无需新增配置项

### REQ-006: 依赖版本一致性
为避免 Spring Boot 2.7 BOM 降级 nacos-client 3.0.1 的传递依赖，父 pom 必须在 `<dependencyManagement>` 中显式锁定：
- jackson-core / jackson-databind / jackson-annotations = 2.18.x
- httpclient5 = 5.4.x
- httpcore5 = 5.3.x

## Scenario: Eureka → Nacos 3.x 同步
1. 用户在 cluster 配置中新增一个 Nacos 3.x 集群（serverAddr、username、password）
2. NacosServerHolder 通过 `NamingFactory.createNamingService` 创建并缓存 NamingService 实例
3. EurekaSyncToNacosServiceImpl 调用 `namingService.registerInstance(...)` 注册实例
4. Nacos 3.x 服务端通过 gRPC（端口 9849）接收注册请求

## Scenario: 批量同步所有服务（batch-sync-all）
1. 用户触发"一键同步某集群所有服务"
2. TaskAddAllProcessor 调用 `namingService.getServicesOfServer(pageNo, pageSize, groupName)` 分页列举
3. 对每个返回的 serviceName 调用 `getAllInstances` 获取实例列表
4. 为每个服务构造 `TaskAddRequest` 并触发单独的同步任务
