# Spec: batch-sync-all

## Overview
"批量同步所有服务"能力：当用户指定一个源集群（source cluster）时，nacos-sync 自动拉取该集群下所有服务名，并为每个服务创建独立的同步任务，实现"一键同步全部"。

## Requirements

### REQ-001: 触发方式
- 通过 [TaskAddAllProcessor](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/template/processor/TaskAddAllProcessor.java) 处理 `TaskAddAllRequest`
- 输入：源集群 ID、目标集群 ID、分组名（可选）

### REQ-002: 服务列表列举
- **当源集群为 Nacos 时**：调用 `NamingService.getServicesOfServer(pageNo, pageSize, groupName)` 分页拉取服务名列表
  - 起始页：1
  - 每页大小：1000（或可配置）
  - 循环拉取直到某页返回数量 < pageSize
- **当源集群为 Eureka/Consul/Zookeeper 时**：走各自原有的列举逻辑，本次升级不涉及

### REQ-003: 过滤逻辑
- 仅对"目标集群为当前实例所在 cluster"的服务创建同步任务
- 同一个 (serviceName, sourceClusterId, destClusterId) 不重复创建任务

### REQ-004: 禁止使用反射
- 列举服务列表必须使用 `NamingService` 公共 API
- 禁止通过反射访问 `NacosNamingService.serverProxy` 等内部字段
- 禁止调用 `/nacos/v1/ns/catalog/services` 或任何 v1/v2 HTTP API（Nacos 3.2.0+ 已移除）

### REQ-005: 行为兼容性
升级后的批量同步行为必须与升级前保持一致：
- 同样支持分页拉取所有服务
- 同样支持按目标集群过滤
- 同样为每个服务构造 `TaskAddRequest` 并交给 [TaskAddProcessor](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/template/processor/TaskAddProcessor.java) 处理
- 鉴权复用源集群配置，无需管理员账号

## Scenario: 正常批量同步
1. 用户请求：源集群 Nacos-A，目标集群 Nacos-B
2. TaskAddAllProcessor 调用 `NamingService.getServicesOfServer(1, 1000, "DEFAULT_GROUP")` 获取第一页
3. 对每个 serviceName 构造 `TaskAddRequest` 并触发
4. 继续翻页直到拉取完毕
5. 返回创建的任务总数

## Scenario: Nacos 3.x 服务端兼容
- nacos-client 3.0.1 通过 gRPC 与 Nacos 3.x 服务端通信
- `getServicesOfServer` 内部走 gRPC 通道，不依赖 HTTP v1/v2 API
- 即使服务端为 3.2.0+（已移除 HTTP API）也能正常工作
