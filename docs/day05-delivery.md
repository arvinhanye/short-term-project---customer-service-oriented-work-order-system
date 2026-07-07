# Day05 交付记录：推荐功能与跨数据库联查

## 1. 交付目标

根据 Day05 要求，本阶段完成个性化推荐功能和跨数据库联查功能，产出 `RecommendService`、联合查询服务和 DTO。

## 2. 个性化推荐

`RecommendService` 已从简单分类去重升级为综合评分推荐：

- MySQL 历史工单：读取用户最近工单，统计常用分类和时间衰减得分。
- MongoDB 行为日志：读取 `action_logs`，按 `CREATE_ITEM`、`ADD_COMMENT`、`RATE`、`VIEW`、`SEARCH` 等行为加权。
- MongoDB 评论评分：读取用户评分和全局平均评分，提升高评分候选。
- 全局热度：结合热门工单行为次数，支持冷启动。
- 输出 `RecommendationDTO`，包含推荐工单、分类、分数、推荐原因、用户历史次数、全局热度和评分指标。

保留 `recommendCategories` 方法，兼容现有创建工单界面的推荐分类入口。

## 3. 跨数据库联查

新增 `CrossDatabaseQueryService`，以 MySQL 主键作为 MongoDB 文档引用字段进行组装：

- `getTicket`：按 `item_id` 联查 MySQL 工单主数据、订单、分类、用户、资料，以及 MongoDB 详情、评论、行为日志。
- `pageTickets`：分页联查工单列表，管理员可看全部，普通用户仅看自己的工单。
- `searchTickets`：按 MySQL 工单标题搜索，再补齐 MongoDB 详情与日志数据。
- `getUserActivity`：按 `user_id` 联查用户资料、最近工单、最近评论和最近行为日志。

## 4. DTO

新增 DTO：

| DTO | 说明 |
| --- | --- |
| `RecommendationDTO` | 推荐结果，包含分数和推荐原因 |
| `CrossTicketDTO` | 跨 MySQL/MongoDB 的工单聚合视图 |
| `UserActivityDTO` | 用户活动聚合视图 |

`CrossTicketDTO` 包含 `consistencyWarnings`，用于暴露跨库数据缺失或状态不一致问题。

## 5. 自查结果

- SQL 全部继续使用 `PreparedStatement` / `CallableStatement`，未新增字符串拼接 SQL。
- 写操作仍由 DAO 事务封装或显式事务管理。
- 密码模块未改动，仍使用 BCrypt。
- MongoDB 继续通过 MySQL `item_id`、`user_id` 字符串引用主数据。
- 联查服务不反向依赖 MongoDB `_id` 作为业务 ID，避免跨库主键混乱。

## 6. 验证结果

```text
mvn test
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 7. Day05 提交记录

建议提交信息：

```text
Day05 recommendation and cross database query
```
