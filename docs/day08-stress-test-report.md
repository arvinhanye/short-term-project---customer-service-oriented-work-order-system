# Day08 压力测试报告

## 1. 测试目标

根据 Day08 要求，本阶段补充行为日志压力测试代码，用于验证 MongoDB 行为日志写入在批量 10000 条日志、50 并发场景下的稳定性。

## 2. 测试范围

| 项目 | 说明 |
| --- | --- |
| 测试类 | `src/test/java/com/ticket/performance/ActionLogStressTest.java` |
| 日志集合 | MongoDB `action_logs` |
| 写入总量 | 10000 条 |
| 并发数 | 50 |
| 日志类型 | `VIEW` / `SEARCH` |
| 客户端标识 | `STRESS` |

## 3. 执行方式

压力测试默认不随 `mvn test` 执行，避免普通单元测试依赖本机 MongoDB 或被压力测试拖慢。

在本机 MySQL/MongoDB 环境完成初始化后，可执行：

```bash
mvn -Dstress=true -Dtest=com.ticket.performance.ActionLogStressTest test
```

测试成功时控制台会输出写入总量、并发数和耗时，例如：

```text
Inserted 10000 action logs with 50 workers in <elapsed> ms
```

## 4. 本次验证结果

默认单元测试构建已验证压力测试不会误触发：

```text
mvn test
Tests run: 16, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

其中 Skipped 的 1 项为 `ActionLogStressTest`，需通过 `-Dstress=true` 显式开启。

已在本机 MongoDB 环境执行完整压力测试：

```text
mvn -Dstress=true -Dtest=com.ticket.performance.ActionLogStressTest test
Inserted 10000 action logs with 50 workers in 1553 ms
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

MongoDB 写入数量校验：

| 指标 | 数量 |
| --- | --- |
| 压测前 `action_logs` | 106 |
| 压测新增日志 | 10000 |
| 压测后 `action_logs` | 10106 |

## 5. 风险与建议

- 压力测试会向 MongoDB `action_logs` 写入真实测试数据，建议在测试库执行。
- 执行前确认 `mongodb.uri` 和 `mongodb.database` 指向测试环境。
- 若本机资源较弱，可临时调低 `TOTAL_LOGS` 或 `CONCURRENCY` 后再恢复提交版本。
