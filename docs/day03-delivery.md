# Day03 交付记录：核心业务模块与 MongoDB DAO

## 1. 交付目标

根据 Day03 要求，本阶段完成用户模块、核心业务模块 MySQL 部分、MongoDB DAO 与业务 Service 的代码闭环，并按安全与一致性要求完成自查。

## 2. 用户模块

- `UserService`：注册、登录、资料维护、用户启用/禁用、管理员用户列表。
- `UserDAO`：用户 CRUD、按用户名/邮箱/角色/状态查询、密码哈希更新。
- `ProfileDAO`：用户档案查询与保存。
- `PasswordUtil`：统一使用 BCrypt 生成和校验密码哈希。

## 3. 核心业务模块

- `BusinessService`：工单创建、我的工单分页、详情聚合、客户回复、客服回复、内部备注、评价、状态流转、客服分配。
- `ItemDAO`：工单主数据写入、查询、标题/分类更新、分页搜索。
- `OrderDAO`：订单/记录写入、按用户和状态分页、按工单查询、状态更新。
- `CategoryDAO`：分类查询、增删改、子分类和工单占用统计。
- `RecommendService`：根据用户最近工单推荐常用分类。
- `StatisticsService`：MySQL 存储过程报表和 MongoDB 聚合统计。

## 4. MongoDB DAO 与文档模型

MongoDB 用于保存关系型表不适合承载的详情、回复和日志数据，并通过 MySQL 主键字符串作为引用字段：

| 集合 | DAO | 设计说明 |
| --- | --- | --- |
| `item_details` | `DetailDAO` | 以 `item_id` 引用 MySQL `items.item_id`，嵌入 `metadata` 保存优先级、创建人、处理人、渠道和处理时间 |
| `comments` | `CommentDAO` | 以 `item_id`、`user_id` 引用 MySQL 主键，使用 `tags` 数组区分客户回复、客服回复、内部备注和评分 |
| `action_logs` | `LogDAO` | 保存用户行为流水，嵌入 `client_info` 记录客户端类型和 IP |
| `system_logs` | `SystemLogDAO` | 保存审计与异常日志，嵌入 `action_detail` 记录操作上下文 |

## 5. 一致性与安全自查

- SQL 全部通过 `PreparedStatement` 或其子接口 `CallableStatement` 执行，未使用字符串拼接注入参数。
- MySQL 写操作显式执行 `setAutoCommit(false)`、`commit()` 和 `rollback()`；通用 `BaseDAO.update` 已统一走事务回调。
- 注册与测试数据均使用 BCrypt 哈希，不保存明文密码。
- MongoDB 文档使用嵌套对象、数组和日志集合承载详情与行为数据，避免简单照搬关系表。
- 跨库写入时先取得 MySQL 生成 ID，再写入 MongoDB 引用字段；创建工单失败时对 MongoDB 详情文档执行补偿删除。

## 6. 验证结果

```text
mvn test
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 7. Day03 提交记录

建议提交信息：

```text
Day03 core business modules and MongoDB DAO
```
