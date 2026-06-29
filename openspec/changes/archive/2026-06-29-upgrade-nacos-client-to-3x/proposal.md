# Proposal: Upgrade nacos-client to 3.x

## Summary
将 `nacos-client` 依赖从 1.4.7 升级到 3.0.1，使 nacos-sync 能够连接并同步数据到 Nacos 3.x 服务端。

## Motivation
- Nacos 3.x 服务端已与 Nacos 1.x 客户端**不兼容**（官方升级手册明确说明）
- Nacos 3.2.0+ 服务端已移除 v1/v2 HTTP API，旧客户端的所有 HTTP 调用都会失败
- 当前 nacos-sync 使用 nacos-client 1.4.7，无法作为同步工具连接 Nacos 3.x 集群
- 业务需求：用户需要把 Eureka 注册信息同步到 Nacos 3.x 版本

## Scope

### In scope
- 升级 `nacos-client` 依赖：1.4.7 → 3.0.1
- 修复因升级导致的编译错误（5 处 `CollectionUtils` 引用 + 1 处 `TaskAddAllProcessor` 反射调用）
- 在父 pom 中锁定传递依赖版本，避免 Spring Boot 2.7 BOM 降级
- 升级 jacoco-maven-plugin 以兼容新版 nacos-client
- 移除对 Nacos 1.x 内部类（`NacosNamingService`、`NamingProxy`、`UtilAndComs`、`HttpMethod`）的反射依赖

### Out of scope
- 不升级 Spring Boot / Spring Cloud 主版本
- 不保留对 Nacos 1.x 服务端的支持（明确放弃）
- 不引入 `nacos-maintainer-client` 运维 SDK（验证后确认不需要）
- 不修改 Eureka / Consul / Zookeeper 客户端版本
- 不修改前端控制台代码

## Approach

### 核心决策
1. **直接升级到 nacos-client 3.0.1**，不经过 2.x 过渡
2. **`TaskAddAllProcessor` 重写方案**：用 `NamingService.getServicesOfServer()` 公共 API 替代反射调用 `NamingProxy.reqApi("/catalog/services")`
3. **依赖版本锁定**：在父 pom 的 `<dependencyManagement>` 显式声明 jackson / httpclient5 版本，避免 Spring Boot 2.7 BOM 降级
4. **`CollectionUtils` 替换**：改用 `org.springframework.util.CollectionUtils`

### 验证依据
- 通过反射探测 nacos-client 3.0.1 的 `NamingService` 接口，确认 `getServicesOfServer` 4 个重载、`getAllInstances`、`registerInstance`、`deregisterInstance`、`subscribe`、`unsubscribe` 全部保留
- 编译验证发现 8 处错误，其中 5 处为 `CollectionUtils` import，3 处为 `TaskAddAllProcessor` 反射访问 Nacos 1.x 内部类
- 依赖树验证发现 Spring Boot 2.7 BOM 强制降级 jackson 2.18.3 → 2.13.5、httpclient5 5.4.2 → 5.1.4

## Risks
| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| jackson 2.13 vs 2.18 反序列化行为差异 | 中 | 在 dependencyManagement 锁定 2.18.x |
| httpclient5 5.1 vs 5.4 API 差异 | 中 | 在 dependencyManagement 锁定 5.4.x |
| `TaskAddAllProcessor` 重写后行为不一致 | 中 | 保留分页逻辑，编写单测覆盖 |
| Jacoco agent 与新版 nacos-client 不兼容 | 低 | 升级 jacoco 到 0.8.12 |
| Nacos 3.x gRPC 端口（9849）需服务端开放 | 低 | 文档说明，运维侧配置 |

## Open Questions
- 无（探索阶段已全部澄清）

## Affected Specs
- `specs/nacos-sync-client/spec.md`（新增）— Nacos 客户端封装与版本要求
- `specs/batch-sync-all/spec.md`（新增）— 批量同步所有服务的能力规格

## Status
Proposed — 待用户审核批准后进入实施阶段。
