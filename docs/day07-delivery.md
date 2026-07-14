# Day07 交付记录：性能优化、安全加固与系统集成

## 1. 交付目标

根据 Day07 要求，本阶段完成索引优化、SQL 优化、批处理优化、安全加固、全模块联调入口和稳定版代码整理。

## 2. 上午：性能优化与安全加固

### 2.1 索引优化

- MySQL 新增工单分页、状态筛选和标题检索相关索引。
- MongoDB 继续使用 Day06 补充的系统日志复合索引。
- 新增 `mysql_day07_optimization.sql`，支持已初始化数据库补索引和查看 `EXPLAIN`。

### 2.2 SQL 优化

`OrderDAO` 将原来的 `(? IS NULL OR status = ?)` 查询拆分为有状态筛选和无状态筛选两条 SQL，让 `user_id/status/created_at` 组合索引更稳定生效。

### 2.3 批处理优化

新增 `MaintenanceService`：

- 调用 `sp_batch_update_order_status` 存储过程。
- 复用工单状态流转校验。
- 批处理结果写入 MongoDB 系统日志。
- 管理员工作台新增“批量取消超时”入口。

### 2.4 安全加固

- `PasswordUtil` 增加密码强度校验。
- 当前密码策略已升级为 12 到 64 位，并拦截常见密码、系统名和账号信息。
- 登录密码校验对空 hash 安全返回 false。
- 创建工单补充分类存在性、金额精度和描述长度校验。
- 用户资料保存补充长度校验。

## 3. 下午：系统集成、端到端测试与 Bug 修复

新增 `SystemHealthService` 和 `HealthCheckDTO`：

- MySQL 连接自检。
- MongoDB ping 自检。
- 分类 DAO 查询自检。
- 工单分页查询自检。

管理员工作台新增“系统自检”按钮，作为稳定版端到端联调入口。

## 4. 本阶段代码产出

| 文件 | 说明 |
| --- | --- |
| `src/main/java/com/ticket/dao/mysql/OrderDAO.java` | 分页 SQL 优化，增加测试构造器 |
| `src/main/java/com/ticket/service/MaintenanceService.java` | 批处理存储过程调用 |
| `src/main/java/com/ticket/service/SystemHealthService.java` | 系统健康检查 |
| `src/main/java/com/ticket/dto/HealthCheckDTO.java` | 健康检查结果 DTO |
| `src/main/java/com/ticket/util/PasswordUtil.java` | 密码强度和空值安全校验 |
| `src/main/java/com/ticket/service/UserService.java` | 注册和资料输入安全校验 |
| `src/main/java/com/ticket/service/BusinessService.java` | 工单创建输入安全校验 |
| `src/main/java/com/ticket/ui/admin/AdminWorkbenchPanel.java` | 接入系统自检和批处理入口 |
| `src/main/resources/sql/mysql_schema.sql` | 新增性能索引 |
| `src/main/resources/sql/mysql_day07_optimization.sql` | Day07 既有库优化脚本 |
| `docs/day07-performance-report.md` | 性能优化报告 |
| `docs/day07-security-checklist.md` | 安全检查清单 |

## 5. 与需求文档一致性

- 普通用户分页查看本人工单：`OrderDAO.pageByUserAndStatus` 已优化。
- 管理员查看全部工单和状态筛选：`OrderDAO.pageAllByStatus` 已优化。
- 管理员统计和审计：保留 Day06 统计面板与系统日志索引。
- 敏感操作权限：统计、自检、批处理均要求 ADMIN。
- 工单状态流转规则：批处理复用 `BusinessService.validateStatusTransition`。
- 密码安全：符合需求中的 BCrypt 存储并新增强密码策略。

## 6. 验证结果

```text
mvn test
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn package
BUILD SUCCESS
```

## 7. Day07 提交记录

建议提交信息：

```text
Day07 performance security and integration hardening
```
