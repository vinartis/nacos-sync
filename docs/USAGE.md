# nacos-sync 使用说明

## 概述

nacos-sync 是一个跨注册中心同步工具，用于在 Eureka、Nacos、Consul、Zookeeper 等不同注册中心之间同步服务实例数据。支持 Web 控制台和 REST API 两种管理方式。

## 支持的同步链路

| 源集群 | 目标集群 | 支持 | 备注 |
|--------|---------|------|------|
| Eureka | Nacos | ✅ | 仅支持 Spring Cloud 注册中心 |
| Consul | Nacos | ✅ | 仅支持 Spring Cloud 注册中心 |
| Zookeeper | Nacos | ✅ | 仅支持 Dubbo 注册中心 |
| Nacos | Nacos | ✅ | 仅支持同版本 Nacos 之间迁移 |
| Nacos | Eureka | ✅ | 仅支持 Spring Cloud 注册中心 |
| Nacos | Consul | ✅ | 仅支持 Spring Cloud 注册中心 |
| Nacos | Zookeeper | ✅ | 仅支持 Dubbo 注册中心 |

## Nacos 服务端兼容性

| Nacos 服务端版本 | 支持 | 备注 |
|----------------|------|------|
| 3.x | ✅ | 完全支持。服务端需开放 gRPC 端口（默认 `server.port + 1000`，如 8848 对应 9849） |
| 2.x | ✅ | 完全支持。服务端需开放 gRPC 端口 |
| 1.x | ❌ | 不支持。nacos-sync 使用 nacos-client 3.0.1，与 Nacos 1.x 服务端不兼容 |

> **重要**：Nacos 3.2.0+ 服务端移除了 v1/v2 HTTP API。nacos-sync 通过 gRPC 与 Nacos 3.x 通信，无需关心 HTTP API 兼容性。

## 环境要求

- **操作系统**：64bit Linux/Unix/Mac/Windows，推荐 Linux/Unix/Mac
- **JDK**：17 或更高版本
- **Maven**：3.5.2+
- **MySQL**：5.6+

## 快速开始

### 1. 下载与构建

```bash
git clone <repo-url>
cd nacos-sync
mvn clean package -U
```

构建产物位于：
```
nacos-sync/nacossync-distribution/target/nacos-sync-0.5.0.tar.gz
```

### 2. 解压安装包

```bash
tar -xzf nacos-sync-0.5.0.tar.gz
cd nacos-sync
```

目录结构：
```
nacos-sync
├── LICENSE
├── NOTICE
├── bin
│   ├── nacosSync.sql       # 建表脚本
│   ├── shutdown.sh          # 停止脚本（Linux/Mac）
│   ├── startup.sh           # 启动脚本（Linux/Mac）
│   └── startup.bat          # 启动脚本（Windows）
├── conf
│   ├── application.properties
│   └── logback-spring.xml
├── logs
└── nacos-sync-server.jar
```

### 3. 初始化数据库

默认使用 MySQL 数据库。

1. 创建数据库 schema，默认名为 `nacos_sync`：
   ```sql
   CREATE DATABASE nacos_sync DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
   ```
2. 应用启动时 Hibernate 会自动建表（`ddl-auto=update`）
3. 若自动建表失败，使用 `bin/nacosSync.sql` 手动建表

### 4. 配置数据库连接

编辑 `conf/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/nacos_sync?characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=root
```

### 5. 启动服务

**Linux/Mac**：
```bash
cd bin
sh startup.sh start
```

**Windows**：
```cmd
cd bin
startup.bat
```

### 6. 访问控制台

- **Web 控制台**：http://127.0.0.1:8083/
- **Swagger API 文档**：http://127.0.0.1:8083/swagger-ui.html#/

默认服务端口为 `8083`，可在 `conf/application.properties` 中修改。

## 配置集群

### 添加 Nacos 3.x 集群

在控制台「Cluster Config」页面添加一个新的 Nacos 集群：

| 字段 | 说明 | 示例 |
|------|------|------|
| Cluster Name | 集群名称 | nacos-prod |
| Cluster Type | 集群类型 | Nacos |
| Connect Key List | Nacos 服务端地址（多个用逗号分隔） | 192.168.1.10:8848,192.168.1.11:8848 |
| User Name | Nacos 鉴权用户名 | nacos |
| Password | Nacos 鉴权密码 | nacos |
| Namespace | 命名空间 ID（public 留空） | |

> **提示**：Nacos 3.x 默认开启鉴权，请确保用户名密码正确，且该账号具有读写权限。

### 添加 Eureka 集群

| 字段 | 说明 | 示例 |
|------|------|------|
| Cluster Type | 集群类型 | Eureka |
| Connect Key List | Eureka 服务端地址 | 192.168.1.20:8761 |

## 创建同步任务

### 单个服务同步

在「Service Sync」页面，填写以下信息：

- **Source Cluster**：选择源集群
- **Destination Cluster**：选择目标集群
- **Service Name**：要同步的服务名
- **Group Name**：分组名（可选，Nacos 默认 `DEFAULT_GROUP`）

### 批量同步所有服务（Nacos → Nacos）

当源集群为 Nacos 时，在「Service Name」字段输入 `All`，系统会自动拉取源集群下所有服务名，并为每个服务创建独立的同步任务。

> **注意**：此功能使用 `NamingService.getServicesOfServer()` 公共 API 分页拉取服务列表，兼容 Nacos 2.x 和 3.x 服务端。

### 批量同步所有服务（Zookeeper → Nacos，Dubbo）

当源集群为 Zookeeper 时，在「Service Name」字段输入 `*`，系统会全量同步 Zookeeper 中所有 Dubbo 服务到 Nacos。

## 常见问题

### 1. 启动报错 `$'\r': command not found`

**原因**：在 Windows 环境下用 Git Bash 执行 `.sh` 脚本时，CRLF 换行符导致 bash 解析失败。

**解决**：
```bash
# 将脚本转换为 LF 换行符
sed -i 's/\r$//' bin/startup.sh bin/shutdown.sh

# 或使用 dos2unix
dos2unix bin/startup.sh bin/shutdown.sh
```

### 2. 连接 Nacos 3.x 失败

**可能原因**：
1. **gRPC 端口未开放**：Nacos 3.x 需要 gRPC 端口（默认 `server.port + 1000`，如 8848 对应 9849）开放
2. **鉴权失败**：Nacos 3.x 默认开启鉴权，检查用户名密码
3. **连接 1.x 服务端**：nacos-sync 不再支持 Nacos 1.x 服务端

**排查步骤**：
1. 确认 Nacos 服务端版本：`curl http://<nacos-host>:8848/nacos/v1/console/server/state`
2. 确认 gRPC 端口可达：`telnet <nacos-host> 9849`
3. 查看启动日志：`tail -f logs/nacos-sync-start.out`

### 3. 同步任务状态一直是 SYNC

检查 `worker_ip` 字段是否有 worker 实例在工作。多个 nacos-sync 实例会通过数据库抢占任务。

### 4. Mockito 测试失败

**原因**：项目当前使用 Mockito 1.10.19，与 JDK 17 不兼容（cglib 访问 `ClassLoader.defineClass` 被强模块封装禁止）。

**影响范围**：仅影响以下 4 个测试类，不影响生产功能：
- `ConsulSyncToNacosServiceImplTest`
- `EurekaSyncToNacosServiceImplTest`
- `NacosSyncToNacosServiceImplTest`
- `NacosSyncToZookeeperServiceImplTest`

**临时绕过**：运行测试时加 `-Dtest=!*ServiceImplTest` 跳过这些测试。

## REST API 速览

完整 API 文档见 Swagger UI：http://127.0.0.1:8083/swagger-ui.html#/

主要接口：

| 接口 | 说明 |
|------|------|
| `POST /cluster/add` | 添加集群配置 |
| `GET /cluster/list` | 查询集群列表 |
| `POST /task/add` | 添加单个同步任务 |
| `POST /task/addAll` | 批量同步所有服务 |
| `GET /task/list` | 查询任务列表 |
| `POST /task/delete` | 删除任务 |
| `POST /task/cacheReset` | 重置任务缓存 |

## 高级配置

### 自定义 JVM 参数

编辑 `bin/startup.sh` 中的 `JAVA_OPT`：
```bash
JAVA_OPT="${JAVA_OPT} -server -Xms2g -Xmx2g -Xmn1g -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"
```

### 修改服务端口

编辑 `conf/application.properties`：
```properties
server.port=8083
```

### 健康检查

```bash
curl http://127.0.0.1:8083/actuator/health
```

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 2.7.17 |
| Spring Cloud | 2021.0.3 |
| nacos-client | 3.0.1 |
| jackson | 2.18.3 |
| httpclient5 | 5.4.2 |
| JDK | 17+ |

## 相关文档

- [架构决策记录](architecture-decisions/ADR-001-upgrade-nacos-client-to-3x.md)
- [OpenSpec 变更提案](../openspec/changes/upgrade-nacos-client-to-3x/proposal.md)
- [设计文档](../openspec/changes/upgrade-nacos-client-to-3x/design.md)
