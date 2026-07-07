# Day07 性能优化报告

## 1. 优化目标

本阶段围绕“工单列表分页、状态筛选、批量状态流转、统计审计查询”进行性能优化，确保系统符合需求文档中的普通用户工单分页、管理员全量工单查看、统计报表和审计日志查看能力。

## 2. 索引优化

### 2.1 MySQL

新增或调整索引：

| 表 | 索引 | 优化场景 |
| --- | --- | --- |
| `items` | `idx_items_category_created_at(category_id, created_at)` | 推荐和分类最近工单查询 |
| `items` | `ft_items_title(title)` | 工单标题检索 |
| `orders` | `uk_orders_item_id(item_id)` | 保障工单主表与处理记录一对一 |
| `orders` | `idx_orders_user_created_at(user_id, created_at)` | 普通用户查看本人工单 |
| `orders` | `idx_orders_user_status_created_at(user_id, status, created_at)` | 普通用户按状态筛选 |
| `orders` | `idx_orders_status_created_at(status, created_at)` | 管理员按状态筛选 |

已提供 `mysql_day07_optimization.sql`，用于已初始化数据库补充索引并查看关键查询 `EXPLAIN`。

### 2.2 MongoDB

Day06 已补充 `system_logs` 复合索引：

- `{ log_type: 1, timestamp: -1 }`
- `{ log_level: 1, timestamp: -1 }`
- `{ user_id: 1, timestamp: -1 }`

这些索引用于系统日志按类型、级别、用户筛选并按时间倒序展示。

## 3. SQL 优化

`OrderDAO` 原状态筛选使用 `(? IS NULL OR status = ?)` 形式，数据库难以稳定使用组合索引。

本次将分页查询拆成两类 SQL：

- 无状态筛选：`WHERE user_id = ? ORDER BY created_at DESC`
- 有状态筛选：`WHERE user_id = ? AND status = ? ORDER BY created_at DESC`

管理员全量查询也按同样思路拆分，避免 OR 条件导致执行计划不稳定。

## 4. 批处理优化

新增 `MaintenanceService.batchUpdateOrderStatus`，通过 `sp_batch_update_order_status` 存储过程执行批量状态流转：

- 服务层校验 ADMIN 权限。
- 服务层复用工单状态流转规则。
- 存储过程集中执行批量更新。
- 操作结果写入 MongoDB `system_logs`。

管理员工作台新增“批量取消超时”按钮，可将 30 天前仍为待处理的工单批量流转为已取消，并在执行前弹出确认。

## 5. 连接池稳定性

`MySQLDBUtil` 新增可配置参数：

- `mysql.pool.connectionTimeoutMs`
- `mysql.pool.idleTimeoutMs`
- `mysql.pool.maxLifetimeMs`
- `mysql.pool.leakDetectionThresholdMs`

默认值保持保守，避免影响本地开发环境，同时支持后续压测时调整。

## 6. 验证方式

- `mvn test` 覆盖订单分页新 SQL 路径和密码强度策略。
- 管理员端“系统自检”覆盖 MySQL 连接、MongoDB 连接、分类 DAO 查询和工单分页查询。
- `mysql_day07_optimization.sql` 提供关键 SQL 的 `EXPLAIN` 检查入口。
