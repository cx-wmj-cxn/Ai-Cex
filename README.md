# AI CEX 微服务后端骨架

基于你给定的交易所产品架构，已按 Maven 多模块方式搭建 JDK 21 + Spring Boot 微服务基础工程。

## 模块划分

- `cex-dependencies`：统一依赖版本管理（含 Spring Boot 版本与 Spring Cloud Alibaba 预留版本）
- `cex-common`：公共基础模块
- `cex-gateway-service`：统一网关入口（后续可切到 Spring Cloud Gateway）
- `cex-auth-service`：认证与安全中心（登录、2FA、设备管理）
- `cex-account-service`：账户与身份（账户体系、KYC/KYB）
- `cex-wallet-service`：资金钱包（充提、归集、账户账本）
- `cex-trading-service`：交易核心域（订单、撮合、成交事件生产）
- `cex-market-data-service`：行情域（Ticker、深度、K线、行情分发）
- `cex-risk-compliance-service`：风控与合规（AML、提现风控、交易风控）
- `cex-clearing-service`：清结算中心（手续费、返佣、资金费率等）
- `cex-ops-service`：运营与客服后台能力
- `cex-data-service`：数据与报表服务

## 技术基线

- JDK: `21`
- Spring Boot: `3.3.5`
- Build Tool: `Maven`

## 已补齐的通用基础设施

- 统一返回体：`ApiResponse`
- 统一异常处理：`GlobalExceptionHandler` + `BusinessException`
- OpenAPI 文档：`OpenApiConfig`（默认 `/swagger-ui.html`）
- 日志 traceId：`TraceIdFilter`（请求头透传 `X-Trace-Id`）
- 多环境：各服务默认 `spring.profiles.active=dev`
- 可观测性：Actuator 暴露 `health/info/prometheus`

## 预留 Spring Cloud Alibaba 集成

- `cex-dependencies` 增加了 `cloud` profile，内含：
  - `spring-cloud-dependencies`
  - `spring-cloud-alibaba-dependencies`
- 各业务服务都增加了 `cloud` profile 依赖位，已预埋：
  - `spring-cloud-starter-openfeign`（网关为 `spring-cloud-starter-gateway`）
  - `spring-cloud-starter-alibaba-nacos-discovery`
  - `spring-cloud-starter-alibaba-nacos-config`
  - `spring-cloud-starter-alibaba-sentinel`
- 各服务 `application.yml` 已预置 Nacos/Sentinel 参数，默认关闭，不影响当前本地启动。

## 快速构建

```bash
mvn clean install
```

启用云组件依赖（Nacos/Feign/Sentinel）：

```bash
mvn -Pcloud clean install
```

## 模块业务功能文档

各模块业务功能清单见：`docs/README.md`
