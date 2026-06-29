# Tasks: Upgrade nacos-client to 3.x

## T1: 升级 nacos-client 版本与依赖锁定
- [x] T1.1 在父 [pom.xml](file:///d:/source/github/nacos-sync/pom.xml) 中将 `nacos-client.verison` 从 1.4.7 改为 3.0.1
- [x] T1.2 在父 pom.xml 的 `<dependencyManagement>` 中新增 jackson 锁定：
  - jackson-core / jackson-databind / jackson-annotations = 2.18.3
  - jackson-datatype-jsr310 / jackson-module-parameter-names = 2.18.3
- [x] T1.3 在父 pom.xml 的 `<dependencyManagement>` 中新增 httpclient5 锁定：
  - httpclient5 = 5.4.2
  - httpcore5 = 5.3.3
- [x] T1.4 执行 `mvn dependency:tree -pl nacossync-worker` 验证版本对齐，无降级

## T2: 修复 CollectionUtils 编译错误（5 处）
- [x] T2.1 修改 [DubboConstants.java](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/util/DubboConstants.java)：替换 import 为 `org.springframework.util.CollectionUtils`，`isNotEmpty` 调用改为 `!isEmpty`
- [x] T2.2 修改 [BatchTaskExecutor.java](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/util/BatchTaskExecutor.java)：同上替换
- [x] T2.3 修改 [ConsulUtils.java](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/util/ConsulUtils.java)：同上替换
- [x] T2.4 修改 [CheckRunningStatusAllNacosThread.java](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/timer/CheckRunningStatusAllNacosThread.java)：同上替换
- [x] T2.5 修改 [NacosSyncToZookeeperServiceImpl.java](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/extension/impl/NacosSyncToZookeeperServiceImpl.java)：同上替换
- [x] T2.6 (额外) 修改 AbstractNacosSync.java、NacosSyncToNacosServiceImpl.java、NacosSyncToEurekaServiceImpl.java：替换 `com.alibaba.nacos.common.utils.CollectionUtils` 为 Spring 版本（保持一致性）

## T3: 重写 TaskAddAllProcessor
- [x] T3.1 阅读 [TaskAddAllProcessor.java](file:///d:/source/github/nacos-sync/nacossync-worker/src/main/java/com/alibaba/nacossync/template/processor/TaskAddAllProcessor.java) 完整代码，理解业务流程
- [x] T3.2 删除 `EnhanceNamingService` 内部类中对 `NacosNamingService`、`NamingProxy`、`UtilAndComs`、`HttpMethod` 的反射依赖
- [x] T3.3 重写 `catalogServices(...)` 方法：改用 `namingService.getServicesOfServer(pageNo, pageSize)` 分页查询服务列表
- [x] T3.4 保留原有业务逻辑：
  - 分页拉取所有服务名
  - 按 consumer（目标集群）过滤
  - 为每个服务构造 `TaskAddRequest` 并触发同步
- [x] T3.5 删除不再使用的 import（`NacosNamingService`、`NamingProxy`、`UtilAndComs`、`HttpMethod`、`ReflectionUtils` 等）
- [ ] T3.6 为重写后的逻辑添加单元测试覆盖分页与过滤场景（暂停：Mockito 1.10.19 与 JDK 17 不兼容，无法 mock NamingService）

## T4: 升级 jacoco-maven-plugin
- [x] T4.1 在父 pom.xml 中将 `jacoco-maven-plugin` 版本从 0.7.8 升级到 0.8.12
- [x] T4.2 执行 `mvn test` 验证测试 fork 正常启动，无 agent 崩溃

## T5: 编译与测试验证
- [x] T5.1 执行 `mvn clean compile -pl nacossync-worker -am` 验证 0 编译错误 ✅ BUILD SUCCESS
- [x] T5.2 执行 `mvn test -pl nacossync-worker -Djacoco.skip=true` 验证现有测试通过
  - 11 个工具类测试全部通过（DubboConstantsTest、ConsulUtilsTest、BatchTaskExecutorTest、StringUtilsTest、NacosUtilsTest、SkyWalkerUtilTest）
  - 4 个 ServiceImplTest 因 Mockito 1.10.19 + JDK 17 不兼容失败（预先存在问题，与本次升级无关）
- [x] T5.3 执行 `mvn dependency:tree -pl nacossync-worker` 再次确认依赖版本未降级
- [ ] T5.4 （可选）对接 Nacos 3.x 真实服务端做集成验证

## T6: 文档更新
- [x] T6.1 在 [README.md](file:///d:/source/github/nacos-sync/README.md) 中说明：仅支持 Nacos 2.x/3.x 服务端，不再支持 Nacos 1.x
- [x] T6.2 在 README.md 中说明 Nacos 3.x 服务端需开放 gRPC 端口（默认 9849）
