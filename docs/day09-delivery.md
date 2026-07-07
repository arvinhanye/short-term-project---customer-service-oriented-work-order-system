# Day09 交付记录：技术设计文档与用户手册

## 1. 交付目标

根据 Day09 要求，本阶段完成最终文档交付，包含技术设计文档、用户手册和代码仓库提交记录。

## 2. 上午：技术设计文档编写

新增 `docs/technical-design.md`，覆盖内容如下：

| 章节 | 内容 |
| --- | --- |
| 系统概述 | 项目背景、技术栈和总体架构 |
| 分层设计 | UI、Service、DAO、配置层职责 |
| 数据设计 | MySQL 表、视图、存储过程、触发器和 MongoDB 集合 |
| 关键流程 | 注册登录、创建工单、查询详情、回复评价、状态流转、统计审计 |
| 安全设计 | BCrypt、密码强度、账号状态、角色权限、数据隔离和审计日志 |
| 异常一致性 | 业务异常、数据库异常、MySQL 事务和 MongoDB 补偿 |
| 性能设计 | 连接池、MySQL 索引、MongoDB 索引和分页查询 |
| 测试设计 | 单元测试、事务测试、压力测试和验证命令 |
| 部署运行 | 初始化数据库、配置连接、打包启动 |
| 扩展建议 | 工单处理 UI、附件管理、权限细化、报表导出和日志归档 |

## 3. 下午：用户手册编写

新增 `docs/user-manual.md`，覆盖内容如下：

| 章节 | 内容 |
| --- | --- |
| 环境准备 | JDK、Maven、MySQL、MongoDB 版本要求 |
| 数据库初始化 | MySQL 和 MongoDB 脚本执行顺序 |
| 构建启动 | `mvn test`、`mvn clean package`、`java -jar` |
| 默认账号 | 管理员、普通用户和禁用用户示例账号 |
| 登录注册 | 登录、注册和密码规则 |
| 普通用户操作 | 我的工单、创建工单、推荐分类、个人资料、退出登录 |
| 管理员操作 | 统计报表、用户管理、系统自检、批量取消超时、退出登录 |
| 验收建议 | 课程验收操作顺序 |
| 常见问题 | 登录、连接、分类、报表、MongoDB 聚合和压力测试问题处理 |

## 4. 配套修正

为了保证用户手册中的默认账号可以直接用于验收，已将 `src/main/resources/sql/mysql_init_data.sql` 中示例账号的 BCrypt 哈希统一更新为 jBCrypt 可识别的 `$2a$` 哈希，对应默认密码：

```text
Ticket@123
```

## 5. 交付物清单

| 交付物 | 路径 |
| --- | --- |
| 技术设计文档 | `docs/technical-design.md` |
| 用户手册 | `docs/user-manual.md` |
| Day09 交付记录 | `docs/day09-delivery.md` |
| README 交付清单 | `README.md` |
| 初始化数据修正 | `src/main/resources/sql/mysql_init_data.sql` |

## 6. 验证结果

建议执行：

```bash
mvn test
mvn clean package
```

数据库初始化后可使用以下账号验收：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin01` | `Ticket@123` |
| 普通用户 | `user01` | `Ticket@123` |
| 禁用用户 | `user06` | `Ticket@123` |

## 7. Day09 提交记录

建议提交信息：

```text
Day09 technical design and user manual
```
