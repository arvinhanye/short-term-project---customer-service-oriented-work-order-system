# Day06 交付记录：数据统计与报表模块

## 1. 交付目标

根据 Day06 要求，本阶段完成数据统计与报表模块，重点覆盖 MongoDB 聚合统计、MySQL 月度报表存储过程调用，以及系统操作日志审计能力。

## 2. 上午：MongoDB 聚合统计

统计服务继续以 `StatisticsService` 作为统一入口，整合行为日志、评论评分和系统日志聚合结果：

- 行为类型分布：统计各类行为次数、独立用户数、平均停留时长和最近行为时间。
- 近 30 天行为趋势：按日期和行为类型聚合，支撑运营趋势查看。
- 热门工单：按 `item_id` 汇总浏览、回复、评分等操作热度。
- 用户活跃度：按用户汇总行为次数、行为类型集合和最后活跃时间。
- 客户端分布：按 `client_info.client_type` 聚合访问来源。
- 评论与评分：输出标签分布、评分分布和工单评论统计。

## 3. 下午：MySQL 存储过程与系统操作日志

月度报表通过 `sp_monthly_report` 存储过程生成，服务层使用 `CallableStatement` 调用：

- 工单总数
- 待处理、处理中、已完成、已关闭、已取消数量
- 月度总金额和平均金额

系统日志模块由 `SystemLogDAO` 负责：

- 写入登录、登录失败、用户状态变更、状态流转、跨库失败等审计日志。
- 支持按类型、级别、用户 ID、关键词筛选最近日志。
- 支持按日志类型、日志级别、用户和近 30 天趋势聚合。
- `mongodb_init.js` 已补充 `system_logs` 常用复合索引。

## 4. 管理员统计报表界面

新增 `AdminStatisticsPanel`，管理员点击“查看统计”后可打开完整统计窗口：

- “月度报表”标签页：选择年份、月份后调用 MySQL 存储过程刷新报表。
- “MongoDB 聚合统计”标签页：通过按钮切换行为、热度、用户、客户端、评论和评分统计。
- “系统日志审计”标签页：支持条件查询、类型汇总、级别汇总、用户汇总和趋势统计。

## 5. 本阶段代码产出

| 文件 | 说明 |
| --- | --- |
| `src/main/java/com/ticket/service/StatisticsService.java` | 增强月度报表和系统日志统计服务 |
| `src/main/java/com/ticket/dao/mongo/SystemLogDAO.java` | 增加审计筛选、级别汇总、用户汇总和趋势聚合 |
| `src/main/java/com/ticket/ui/admin/AdminStatisticsPanel.java` | 新增管理员统计报表面板 |
| `src/main/java/com/ticket/ui/admin/AdminWorkbenchPanel.java` | 接入统计报表窗口 |
| `src/main/resources/sql/mysql_procedures.sql` | 月度报表存储过程补充已取消数量 |
| `src/main/resources/sql/mongodb_init.js` | 补充系统日志复合索引 |

## 6. 自查结果

- MySQL 月报继续通过存储过程和 `CallableStatement` 调用，未新增字符串拼接 SQL。
- MongoDB 聚合管道集中封装在 DAO 层，服务层只负责权限校验和入口组合。
- 系统日志查询对 limit 做边界限制，避免一次性拉取过多审计数据。
- `orders.status = 4` 已纳入月度报表，状态拆分与总数口径保持一致。

## 7. 验证结果

```text
mvn test
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 8. Day06 提交记录

建议提交信息：

```text
Day06 statistics report and audit log module
```
