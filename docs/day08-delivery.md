# Day08 交付记录：单元测试补充、压力测试与代码重构

## 1. 交付目标

根据 Day08 要求，本阶段补充 JUnit5 单元测试、事务回滚测试、行为日志压力测试代码，并对重复日志写入代码进行重构。

## 2. 上午：单元测试补充与事务回滚测试

### 2.1 单元测试补充

新增和完善的测试覆盖：

| 测试类 | 覆盖内容 |
| --- | --- |
| `BaseDAOTransactionTest` | 事务提交和异常回滚 |
| `OrderDAOTest` | 用户工单分页、状态筛选和管理员分页 |
| `UserDAOTest` | 用户 CRUD、密码 BCrypt、弱密码拒绝 |
| `UserServiceSecurityTest` | 登录状态、ADMIN 权限和角色识别 |
| `LogServiceTest` | 行为日志和系统审计日志对象组装 |
| `StatisticsServiceTest` | 工单状态流转规则 |
| `ItemDAOTest` | 非法状态流转防护 |

### 2.2 事务回滚测试

`BaseDAOTransactionTest` 使用 H2 内存库构造事务场景：

- 成功回调应提交插入记录。
- 回调抛出异常时应回滚插入记录。

该测试对应需求文档中的“一致性”要求，确保 DAO 层事务封装具备失败回滚能力。

## 3. 下午：压力测试与代码重构

### 3.1 压力测试

新增 `ActionLogStressTest`：

- 写入总量：10000 条行为日志。
- 并发数：50。
- 目标集合：MongoDB `action_logs`。
- 默认通过系统属性控制，不随普通 `mvn test` 自动执行。

执行命令：

```bash
mvn -Dstress=true -Dtest=com.ticket.performance.ActionLogStressTest test
```

### 3.2 代码重构

新增公共日志服务：

| 类 | 说明 |
| --- | --- |
| `ActionLogService` | 统一写入用户行为日志 |
| `AuditLogService` | 统一写入系统审计日志 |

重构后：

- `UserService` 不再重复构造行为日志和系统日志。
- `BusinessService` 不再重复构造行为日志和系统日志。
- `MaintenanceService` 只保留批处理逻辑，审计写入交由 `AuditLogService`。

## 4. 与需求文档一致性

- 账户与权限：补充 ADMIN 权限和账号状态测试。
- 工单管理：补充分页和状态流转测试。
- 统计与审计：补充日志服务单元测试和压力测试入口。
- 一致性：补充事务回滚测试。
- 可维护性：抽取公共日志服务，减少重复代码。

## 5. 验证结果

```text
mvn test
Tests run: 16, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

mvn package
BUILD SUCCESS

mvn -Dstress=true -Dtest=com.ticket.performance.ActionLogStressTest test
Inserted 10000 action logs with 50 workers in 1553 ms
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

默认测试中 Skipped 的 1 项为显式开关控制的压力测试。连接本机 MongoDB 后已单独开启并通过压力测试，`action_logs` 从 106 增加到 10106。

## 6. Day08 提交记录

建议提交信息：

```text
Day08 unit tests stress test and log refactor
```
