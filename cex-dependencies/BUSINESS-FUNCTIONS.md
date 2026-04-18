# cex-dependencies 模块业务功能清单

## 模块定位

统一管理全项目依赖版本、BOM、构建插件版本，保障多服务版本一致性。

## 必做功能

- 管理 `Spring Boot`、`Spring Cloud`、`Spring Cloud Alibaba` BOM 版本
- 管理常用中间件版本（MyBatis-Plus、Redis、Kafka、RocketMQ、MinIO、JWT）
- 管理测试框架版本（JUnit、MockMvc、Testcontainers）
- 管理构建与质量插件版本（Compiler、Surefire、Spotless、Checkstyle）
- 管理安全扫描插件版本（OWASP dependency-check）

## 后续扩展

- 依赖白名单/黑名单机制
- 统一 CVE 漏洞升级策略
- 多环境构建 profile 模板（dev/test/staging/prod）
