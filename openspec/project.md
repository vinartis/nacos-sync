# Project: nacos-sync

## Overview
nacos-sync 是一个跨注册中心同步工具，用于在 Eureka、Nacos、Consul、Zookeeper 等不同注册中心之间同步服务实例数据。

## Scope
- **In scope**：以 Nacos 为源或目标的同步链路（Eureka→Nacos、Nacos→Eureka、Nacos→Nacos 等）
- **Out of scope**：注册中心自身的功能实现、客户端 SDK 的内部机制

## Stack
- Java 17
- Spring Boot 2.7.17
- Spring Cloud 2021.0.3
- nacos-client（当前 1.4.7）
- Eureka Client（spring-cloud-starter-netflix-eureka-client）
- Consul、Zookeeper 客户端

## Capability Map
| Capability | Description |
|------------|-------------|
| `nacos-sync-client` | 与 Nacos 服务端交互的客户端封装（NamingService 持有、生命周期管理） |
| `eureka-to-nacos-sync` | Eureka 注册信息同步到 Nacos |
| `nacos-to-eureka-sync` | Nacos 注册信息同步到 Eureka |
| `nacos-to-nacos-sync` | Nacos 集群间同步 |
| `consul-to-nacos-sync` | Consul 到 Nacos 的同步 |
| `zookeeper-to-nacos-sync` | Zookeeper 到 Nacos 的同步 |
| `nacos-to-consul-sync` | Nacos 到 Consul 的同步 |
| `nacos-to-zookeeper-sync` | Nacos 到 Zookeeper 的同步 |
| `batch-sync-all` | 批量同步某集群下所有服务的"一键同步"功能 |

## Conventions
- 同步实现统一实现 `SyncService` 接口，并通过 `@NacosSyncService` 注解注册
- 与注册中心交互的客户端实例由 `Holder` 接口的实现管理
- 复杂业务流程通过 `SkyWalkerTemplate` + `Processor` 模式编排
