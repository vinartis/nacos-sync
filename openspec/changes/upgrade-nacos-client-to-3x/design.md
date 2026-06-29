# Design: Upgrade nacos-client to 3.x

## Context
nacos-sync 通过 `NamingService` 接口与 Nacos 服务端交互。升级到 nacos-client 3.0.1 后，存在两类问题：

1. **编译错误**：5 处使用了 nacos-client 内部工具类 `com.alibaba.nacos.client.naming.utils.CollectionUtils`；1 处通过反射访问 Nacos 1.x 内部类 `NamingProxy`
2. **依赖版本降级**：Spring Boot 2.7.17 BOM 强制管理 jackson / httpclient5 版本，导致 nacos-client 3.0.1 要求的较新版本被降级

## Decisions

### D1: 直接升级到 3.0.1，不保留 1.x 兼容

**决策**：`nacos-client.verison` 从 1.4.7 直接改为 3.0.1。

**理由**：
- 用户明确不保留 Nacos 1.x 服务端支持
- nacos-client 3.0.1 同时兼容 Nacos 2.x 和 3.x 服务端
- 2.x 中间过渡版本没有提供额外价值

**影响**：失去对 Nacos 1.x 服务端的支持（用户已确认可接受）。

### D2: TaskAddAllProcessor 重写方案

**原实现**（[TaskAddAllProcessor.java#L150-L214](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/template/processor/TaskAddAllProcessor.java)）：
```java
// 反射获取 NacosNamingService 内部的 serverProxy (NamingProxy)
Field serverProxyField = ReflectionUtils.findField(NacosNamingService.class, "serverProxy");
this.serverProxy = (NamingProxy) ReflectionUtils.getField(serverProxyField, delegate);
// 调用 v1 HTTP API: /nacos/v1/ns/catalog/services
this.serverProxy.reqApi(UtilAndComs.nacosUrlBase + "/catalog/services", params, HttpMethod.GET);
```

**问题**：
1. `NamingProxy` 是 nacos-client 内部类，3.x 已重构，字段名可能变化
2. `/catalog/services` 是 v1 HTTP API，Nacos 3.2.0+ 服务端已移除
3. 反射访问使代码脆弱，依赖具体实现

**新实现**：使用公共 API `NamingService.getServicesOfServer(pageNo, pageSize, groupName)`：
```java
ListView<String> services = namingService.getServicesOfServer(pageNo, pageSize, groupName);
List<String> serviceNames = services.getData();
// 然后对每个 serviceName 调用 getAllInstances 获取实例列表
```

**API 兼容性验证**：通过反射探测 nacos-client 3.0.1 的 `NamingService` 接口，确认存在 4 个重载：
- `getServicesOfServer(int pageNo, int pageSize)`
- `getServicesOfServer(int pageNo, int pageSize, String groupName)`
- `getServicesOfServer(int pageNo, int pageSize, AbstractSelector selector)`
- `getServicesOfServer(int pageNo, int pageSize, String groupName, AbstractSelector selector)`

返回类型 `com.alibaba.nacos.api.naming.pojo.ListView<String>`，与 1.x 一致。

**鉴权**：复用现有 `NacosServerHolder` 创建 NamingService 时的 username/password 配置，无需额外管理员账号。

### D3: CollectionUtils 替换

**原 import**：`com.alibaba.nacos.client.naming.utils.CollectionUtils`（nacos-client 内部类，3.x 已移除）

**新 import**：`org.springframework.util.CollectionUtils`（Spring Core，项目已依赖）

**API 对比**：
| 原 API | 新 API | 兼容性 |
|--------|--------|--------|
| `CollectionUtils.isEmpty(Collection)` | `CollectionUtils.isEmpty(Collection)` | ✅ 完全一致 |
| `CollectionUtils.isNotEmpty(Collection)` | `!CollectionUtils.isEmpty(Collection)` | ⚠️ 需取反 |

**影响文件**（5 处）：
- `DubboConstants.java`
- `BatchTaskExecutor.java`
- `ConsulUtils.java`
- `CheckRunningStatusAllNacosThread.java`
- `NacosSyncToZookeeperServiceImpl.java`

### D4: 依赖版本锁定策略

**问题**：Spring Boot 2.7.17 的 BOM 强制管理以下依赖版本，导致 nacos-client 3.0.1 要求的版本被降级：

| 依赖 | nacos-client 3.0.1 要求 | SB 2.7 BOM 强制 |
|------|----------------------|-----------------|
| jackson-core | 2.18.3 | 2.13.5 |
| jackson-databind | 2.18.3 | 2.13.5 |
| jackson-annotations | 2.18.3 | 2.13.5 |
| httpclient5 | 5.4.2 | 5.1.4 |
| httpcore5 | 5.3.3 | 5.1.5 |

**决策**：在父 [pom.xml](file:///d:/source/github/nacos-sync/pom.xml) 的 `<dependencyManagement>` 中显式声明这些依赖的版本，覆盖 Spring Boot BOM 的管理。

```xml
<dependencyManagement>
    <dependencies>
        <!-- 锁定 jackson 版本，与 nacos-client 3.0.1 对齐 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <!-- ... 其他 jackson 模块 -->
        <!-- 锁定 httpclient5/httpcore5 版本 -->
        <dependency>
            <groupId>org.apache.httpcomponents.client5</groupId>
            <artifactId>httpclient5</artifactId>
            <version>${httpclient5.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**版本选择**：
- `jackson.version` = 2.18.3（与 nacos-client 3.0.1 POM 声明一致）
- `httpclient5.version` = 5.4.2
- `httpcore5.version` = 5.3.3

### D5: Jacoco 升级

**问题**：当前 `jacoco-maven-plugin` 版本 0.7.8（2016 年发布），其 javaagent 与 nacos-client 3.0.1 的 shaded 字节码不兼容，导致 surefire fork JVM 时崩溃，错误：`FATAL ERROR in native method: processing of -javaagent failed`。

**决策**：升级 `jacoco-maven-plugin` 从 0.7.8 → 0.8.12（最新稳定版，支持 JDK 17）。

## Alternatives Considered

### A1: 引入 nacos-maintainer-client 运维 SDK
**否决理由**：验证后 `NamingService.getServicesOfServer()` 已能满足批量同步需求，无需引入新依赖。

### A2: 升级到 nacos-client 2.x 作为过渡
**否决理由**：用户明确不需要保留 1.x 支持，过渡版本没有提供额外价值。

### A3: 移除批量同步功能
**否决理由**：用户未要求移除功能，且公共 API 可满足需求。

## Migration Impact

### 用户侧影响
1. **Nacos 3.x 集群需开放 gRPC 端口**：默认 9849（服务端主端口 + 1000）
2. **配置无需变更**：username/password/namespace/serverAddr 等参数完全兼容
3. **失去 Nacos 1.x 服务端支持**：明确不兼容

### 代码侧影响
- 6 个文件需要修改（5 个 CollectionUtils + 1 个 TaskAddAllProcessor）
- 1 个 pom 文件修改（nacos-client 版本）
- 1 个 pom 文件修改（dependencyManagement 锁定版本）
- 1 个 pom 文件修改（jacoco 升级）

## Test Strategy
1. **单元测试**：现有 `EurekaSyncToNacosServiceImplTest` 等必须全部通过
2. **新增单测**：覆盖 `TaskAddAllProcessor` 重写后的逻辑
3. **集成测试**：对接 Nacos 3.x 真实服务端验证同步链路（人工或 CI）
