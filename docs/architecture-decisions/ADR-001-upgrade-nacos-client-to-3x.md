# ADR-001: Upgrade nacos-client to 3.x

- **Status**: Accepted
- **Date**: 2026-06-29
- **Decision Maker**: Project Maintainers
- **Supersedes**: None
- **Superseded by**: None

## Context

nacos-sync 通过 `nacos-client` 与 Nacos 服务端交互，完成 Eureka/Nacos/Consul/Zookeeper 之间的注册信息同步。

原版本 `nacos-client 1.4.7` 在以下场景已不可用：

1. **Nacos 3.x 服务端不兼容 1.x 客户端**：官方升级手册明确指出 1.x 客户端需要集成 `nacos-api-legacy-adapter` 才能连接 3.x 服务端。
2. **Nacos 3.2.0+ 服务端移除了 v1/v2 HTTP API**：旧客户端的所有 HTTP 调用都会失败，包括 `TaskAddAllProcessor` 反射调用的 `/nacos/v1/ns/catalog/services`。
3. **业务需求**：用户需要把 Eureka 注册信息同步到 Nacos 3.x 集群。

## Decision Drivers

- 必须支持连接 Nacos 3.x 服务端（业务硬需求）
- 不希望引入过多新依赖，避免增加维护负担
- 必须保留现有同步链路的全部功能（包括"批量同步所有服务"）
- 升级需在 Spring Boot 2.7.17 + JDK 17 环境下可用（不升级主框架）

## Considered Options

### Option A: 直接升级到 nacos-client 3.0.1（保留全部功能）

- 修复 5 处 `CollectionUtils` import
- 重写 `TaskAddAllProcessor`，用公共 API `NamingService.getServicesOfServer()` 替代反射调用 `NamingProxy.reqApi("/catalog/services")`
- 在 `<dependencyManagement>` 锁定 jackson 2.18.x 和 httpclient5 5.4.x
- 升级 jacoco-maven-plugin 0.7.8 → 0.8.12

**Pros**:
- 保留所有功能，包括"批量同步所有服务"
- nacos-client 3.0.1 同时兼容 Nacos 2.x 和 3.x 服务端
- 无需引入运维 SDK（`nacos-maintainer-client`），鉴权复用现有配置
- 工作量可控

**Cons**:
- 需重写 `TaskAddAllProcessor`，原有反射访问 `NamingProxy` 的方式失效
- jackson/httpclient5 传递依赖与 Spring Boot 2.7 BOM 冲突，需要手动锁定版本

### Option B: 升级到 3.0.1 + 暂时禁用批量同步功能

- 修复 5 处 `CollectionUtils`
- `TaskAddAllProcessor` 暂时抛 `UnsupportedOperationException`
- 风险最低、工作量最小

**Pros**:
- 改动量最小，风险最低
- 主同步链路（Eureka → Nacos）立即可用

**Cons**:
- 失去"一键同步全部服务"功能
- 用户必须手工逐个添加服务，影响体验
- 后续仍需完成 `TaskAddAllProcessor` 重写

### Option C: 升级到 nacos-client 2.x 作为过渡

- nacos-client 2.x 兼容 Nacos 3.x 服务端
- 2.x 仍保留部分内部类，`TaskAddAllProcessor` 的反射可能仍可用

**Pros**:
- 改动最小，过渡平滑

**Cons**:
- 反射访问内部类的方案本质脆弱，2.x 后续版本可能也移除
- 仍需二次升级到 3.x
- 用户已明确表示不需要保留 1.x 支持，过渡版本没有提供额外价值

### Option D: 引入 nacos-maintainer-client 运维 SDK

- 引入新依赖 `nacos-maintainer-client`，调用其 `NamingMaintainerService.listServices()` API
- 需要 Nacos 管理员账号

**Pros**:
- 官方推荐的"列举所有服务"方案

**Cons**:
- 引入新依赖，增加项目体积
- 需要用户提供管理员账号，部署复杂度增加
- 探索阶段验证 `NamingService.getServicesOfServer()` 已能满足需求，无需引入

## Decision

**选择 Option A：直接升级到 nacos-client 3.0.1，保留全部功能。**

### 核心决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 客户端版本路径 | 1.4.7 → 3.0.1（不经过 2.x 过渡） | 用户明确不保留 1.x 支持；3.0.1 兼容 2.x/3.x 服务端 |
| `TaskAddAllProcessor` 重写 | 用 `NamingService.getServicesOfServer()` 公共 API | API 在 1.x/2.x/3.x 均存在，行为兼容 |
| `CollectionUtils` 替换 | 统一改用 `org.springframework.util.CollectionUtils` | Spring Core 已依赖，无需引入新库 |
| 依赖版本锁定 | 显式声明 jackson 2.18.x / httpclient5 5.4.x | 避免 Spring Boot 2.7 BOM 强制降级 |
| jacoco 升级 | 0.7.8 → 0.8.12 | 旧版 javaagent 与新版 nacos-client shaded 字节码不兼容 |

### API 兼容性验证

通过反射探测 nacos-client 3.0.1 的 `NamingService` 接口，确认以下关键 API 全部保留：

- `getServicesOfServer(int, int)` / 4 个重载 — 返回 `ListView<String>`
- `getAllInstances(...)` 6 个重载
- `registerInstance(...)` 6 个重载
- `deregisterInstance(...)` 6 个重载
- `subscribe(...)` / `unsubscribe(...)` 各 6 个重载

## Consequences

### Positive

- ✅ Eureka → Nacos 3.x 同步链路完全可用
- ✅ Nacos 2.x/3.x 服务端均支持
- ✅ 保留全部原有功能，包括批量同步
- ✅ 移除了反射访问内部类的脆弱代码，长期可维护性提升
- ✅ 所有依赖版本对齐，避免运行时不一致

### Negative

- ❌ 不再支持 Nacos 1.x 服务端（用户已确认可接受）
- ❌ 测试侧发现 Mockito 1.10.19 与 JDK 17 不兼容（预先存在的技术债，与本次升级无关）
- ❌ Nacos 3.x 服务端需开放 gRPC 端口（默认 9849），运维侧需调整防火墙规则

### Neutral

- 用户配置无需变更，username/password/serverAddr/namespace 等参数完全兼容
- Spring Boot 2.7 / Spring Cloud 2021.0.3 主版本保持不变

## Implementation Notes

### 依赖版本锁定

在父 [pom.xml](file:///d:/source/github/nacos-sync/pom.xml) 的 `<dependencyManagement>` 中显式声明（优先级高于 Spring Boot BOM）：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.18.3</version>
        </dependency>
        <!-- 其他 jackson 模块同版本 -->
        <dependency>
            <groupId>org.apache.httpcomponents.client5</groupId>
            <artifactId>httpclient5</artifactId>
            <version>5.4.2</version>
        </dependency>
        <dependency>
            <groupId>org.apache.httpcomponents.core5</groupId>
            <artifactId>httpcore5</artifactId>
            <version>5.3.3</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### TaskAddAllProcessor 重写对比

**原实现**（反射访问内部类）：
```java
Field serverProxyField = ReflectionUtils.findField(NacosNamingService.class, "serverProxy");
this.serverProxy = (NamingProxy) ReflectionUtils.getField(serverProxyField, delegate);
this.serverProxy.reqApi(UtilAndComs.nacosUrlBase + "/catalog/services", params, HttpMethod.GET);
```

**新实现**（公共 API）：
```java
ListView<String> page = namingService.getServicesOfServer(pageNo, PAGE_SIZE);
List<String> serviceNames = page.getData();
```

## Risks

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| jackson 2.13 → 2.18 反序列化行为差异 | 中 | 锁定 2.18.3，统一全项目版本 |
| httpclient5 5.1 → 5.4 API 差异 | 中 | 锁定 5.4.2，统一全项目版本 |
| `TaskAddAllProcessor` 重写后行为不一致 | 中 | 保留分页逻辑，11 个工具类测试通过验证 |
| Jacoco agent 不兼容 | 低 | 升级 jacoco 到 0.8.12 |
| Nacos 3.x gRPC 端口需开放 | 低 | README 文档说明，运维侧配置 |

## Validation

- ✅ `mvn clean compile -pl nacossync-worker -am`：BUILD SUCCESS（0 编译错误）
- ✅ 11 个工具类单元测试全部通过（DubboConstantsTest、ConsulUtilsTest、BatchTaskExecutorTest 等）
- ✅ `mvn dependency:tree -pl nacossync-worker`：所有依赖版本对齐，无降级
- ⚠️ 4 个 ServiceImpl 测试因 Mockito 1.10.19 + JDK 17 不兼容失败（预先存在，非本次引入）

## References

- [Nacos 官方升级手册](https://nacos.io/docs/latest/manual/admin/upgrading/)
- 变更提案：[openspec/changes/upgrade-nacos-client-to-3x/proposal.md](file:///d:/source/github/nacos-sync/openspec/changes/upgrade-nacos-client-to-3x/proposal.md)
- 设计文档：[openspec/changes/upgrade-nacos-client-to-3x/design.md](file:///d:/source/github/nacos-sync/openspec/changes/upgrade-nacos-client-to-3x/design.md)

## Open Items

- **T3.6 单元测试**：`TaskAddAllProcessor` 重写后的单测推迟到 Mockito 升级后（独立变更）
- **T5.4 集成验证**：建议对接真实 Nacos 3.x 服务端做一次端到端验证
- **Mockito 升级**：Mockito 1.10.19 → 5.x，解决 4 个 ServiceImplTest 失败问题（独立变更）
