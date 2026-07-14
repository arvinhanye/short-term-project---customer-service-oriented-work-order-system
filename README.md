# Ticket Management

面向客服的工单管理系统，基于 Java Swing、MySQL 8、MongoDB 5、JDBC、HikariCP 与 MongoDB Java Sync Driver 实现。

## 当前实现范围

- 需求与数据库设计文档：需求规格说明、用例图、MySQL E-R 图、MongoDB 集合设计
- 冻结数据库脚本：MySQL 表、视图、存储过程、触发器与 MongoDB 初始化脚本
- 工程骨架：`model`、`dto`、`exception`、`dao`、`service`、`ui`
- 用户模块基础能力：注册、登录、资料维护、用户状态控制
- 工单模块基础能力：创建工单、查看我的工单、查看详情、回复、评价、状态流转、客服分配
- 统计报表模块：MySQL 月度报表存储过程调用、MongoDB 聚合统计、系统日志审计查询
- 稳定版加固：分页 SQL 优化、性能索引、批处理状态流转、密码强度校验、系统自检
- 测试与重构：JUnit5 单元测试、事务回滚测试、行为日志压力测试入口、公共日志服务与分类服务分层重构
- Swing 人性化工作台：统一主题、登录注册、普通用户工单中心、ADMIN 管理工作台、状态标签和异步加载反馈
- 加分项增强：连接池监控面板、MySQL 读写分离连接池、连接故障自动重连、操作审计日志落库与落盘

## 本次维护更新

- 工单列表改为 MySQL 联表摘要查询，并批量补充 MongoDB 工单详情，避免列表逐行跨库查询。
- 普通用户“我的工单”改用基于“创建时间 + 记录编号”的游标分页；筛选条件变化或退出登录时会重置分页游标和会话缓存。
- MongoDB 行为、评论和审计聚合默认统计近 30 天数据，排行结果默认最多展示 20 条，降低历史日志增长对桌面端查询的影响。
- 管理员工单、用户、自检和统计查询改为后台异步加载，避免数据库访问阻塞 Swing 事件线程。
- UI 按 P0-P3 完成改造：统一视觉规范，减少空白和分散入口；管理员中心采用固定分组导航、单击入口和紧凑工作概览；创建工单采用紧凑双栏布局；管理员用户管理支持筛选、启用与禁用账号；主要弹窗支持 Esc 关闭。
- 分类管理统一为严格两级结构：父分类下拉框只显示一级分类，服务层阻止三级分类和非法降级；用户端分类选择、工单详情与推荐结果显示完整分类路径。
- 分类删除确认框由分类管理窗口持有，不再同时激活管理员主页面；主窗口、业务窗口和所有消息框统一使用工单系统图标，不再显示 Java 运行时图标。
- 用户管理改为“顶部筛选 + 左侧账号列表 + 右侧账号详情与状态操作”，提供筛选重置、启用/禁用数量和当前账号保护；自我禁用与不存在用户校验同步下沉至 `UserService`。
- 统计报表改为月度指标卡、顶部行为统计口径和分行日志筛选/快速汇总；动态 MongoDB 字段统一显示中文表头，空结果、加载状态和页签错误提示互不串扰。
- 系统自检改为独立窗口，通过状态卡和明细表展示五项检查的结果、单项耗时与失败原因，支持安全重复执行和关闭窗口时取消后台任务。
- 连接池监控改为 READ/WRITE 双状态卡、实时对比表和参数页签；自动刷新移至后台并防止任务重叠，模拟占用会根据当前负载缩减连接数并保留业务余量。
- 异步可靠性修复：后台任务绑定登录会话和请求序号，过期结果不会覆盖新账号或新筛选；管理员工单支持完整分页，资料读写不再阻塞 Swing 事件线程。

## 界面与交互规范

- 仍使用 JDK 17 标准 Java Swing，不引入 Web 前端或新的 UI 框架。
- 统一使用浅色页面、白色内容卡片、蓝色主操作、红色危险操作以及状态/优先级颜色标签。
- 数据库读取和工单提交使用 `SwingWorker`，界面显示加载、空结果和失败状态。
- 主窗口最小尺寸为 1040×680；内容区通过分栏、卡片和可滚动表格利用可用空间，避免大面积无意义留白。
- 页签、下拉框和表头使用跨平台 Basic UI 与扁平渲染，避免 macOS 原生阴影和文字基线偏移；界面时间统一显示为 `yyyy-MM-dd HH:mm 北京时间`。
- 创建工单对标题、分类和问题描述执行双层必填校验：缺失字段显示红色边框，窗口右下角显示非模态提示；成功和失败反馈均不再打断用户操作。
- 普通用户与管理员的输入操作统一遵循：单行输入按 Enter 执行当前操作；多行输入按 Enter 提交、Shift+Enter 换行。

## 目录结构

```text
ticket-management/
├── pom.xml
├── README.md
├── docs/
│   ├── requirements.md
│   ├── database-design.md
│   └── day02-delivery.md
├── src/main/java/com/ticket/
├── src/main/resources/
│   ├── db.properties
│   ├── logback.xml
│   └── sql/
└── src/test/java/com/ticket/
```

## 课程交付物

- [需求规格说明书（含用例图）](docs/requirements.md)
- [数据库设计文档（含 MySQL E-R 图与 MongoDB 集合结构）](docs/database-design.md)
- [Day02 交付记录：数据库实现、工程搭建与 DAO 基础](docs/day02-delivery.md)
- [Day03 交付记录：核心业务模块与 MongoDB DAO](docs/day03-delivery.md)
- [Day04 交付记录：行为日志模块与聚合统计](docs/day04-delivery.md)
- [Day05 交付记录：推荐功能与跨数据库联查](docs/day05-delivery.md)
- [Day06 交付记录：数据统计与报表模块](docs/day06-delivery.md)
- [Day07 交付记录：性能优化、安全加固与系统集成](docs/day07-delivery.md)
- [Day07 性能优化报告](docs/day07-performance-report.md)
- [Day07 安全检查清单](docs/day07-security-checklist.md)
- [Day08 交付记录：单元测试补充、压力测试与代码重构](docs/day08-delivery.md)
- [Day08 压力测试报告](docs/day08-stress-test-report.md)
- [技术设计文档](docs/technical-design.md)
- [用户手册](docs/user-manual.md)
- [Day09 交付记录：技术设计文档与用户手册](docs/day09-delivery.md)
- [维护更新记录：分页、跨库查询与工作台体验](docs/day10-delivery.md)
- 代码仓库远程：`origin` 指向 Gitee，`github` 指向 GitHub

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- MongoDB 5.0+

## 数据库初始化

按以下顺序执行：

1. `src/main/resources/sql/mysql_schema.sql`
2. `src/main/resources/sql/mysql_views.sql`
3. `src/main/resources/sql/mysql_procedures.sql`
4. `src/main/resources/sql/mysql_triggers.sql`
5. `src/main/resources/sql/mysql_init_data.sql`
6. `src/main/resources/sql/mongodb_init.js`

已按旧版本初始化的数据库，应额外执行 `src/main/resources/sql/mysql_p0_reliability.sql`，以创建 MongoDB 日志重试队列与跨库补偿修复记录表。

已按旧版本初始化的数据库还必须执行一次 `src/main/resources/sql/mysql_password_security.sql`。该脚本增加登录保护与强制换密字段，并轮换内置示例账号的重复密码；执行前请备份数据库。

如果数据库已经按旧版本初始化，可额外执行 `src/main/resources/sql/mysql_day07_optimization.sql` 补充 Day07 性能索引。

## 配置说明

数据库连接配置位于 [db.properties](src/main/resources/db.properties)：

- `mysql.url`
- `mysql.username`
- `mysql.password`
- `mysql.write.url` / `mysql.write.username` / `mysql.write.password`
- `mysql.read.url` / `mysql.read.username` / `mysql.read.password`
- `mysql.write.pool.maximumPoolSize` / `mysql.read.pool.maximumPoolSize`
- `mongodb.uri`
- `mongodb.database`

默认读写库都指向同一个本机 MySQL，便于单机运行；此时读写两个连接池不会带来真正的读写分流。桌面客户端默认使用 WRITE 4 条、READ 2 条连接，避免多客户端部署时连接数线性膨胀。如有 MySQL 主从或只读副本，可把 `mysql.read.url` 改为只读实例地址，DAO 层的 `SELECT` 会走 READ 池，写入、更新和事务会走 WRITE 池。

数据库用户名和密码不再写入资源文件。请使用最小权限的 MySQL 专用账号，并设置环境变量 `TICKET_MYSQL_USERNAME`、`TICKET_MYSQL_PASSWORD`；读写库分开部署时可分别设置 `TICKET_MYSQL_WRITE_USERNAME/PASSWORD` 与 `TICKET_MYSQL_READ_USERNAME/PASSWORD`。应用会拒绝空密码和 `root` 账号。macOS 启动器默认使用 `ticket_app`，优先从登录钥匙串的 `ticket-management-mysql` 项读取密码，并在启动 Java 前验证账号；钥匙串未配置时只询问密码，不再要求手动填写用户名。

## 加分项说明

- 连接池监控面板：ADMIN 工作台点击“连接池监控”，可通过状态卡和对比表查看 READ/WRITE 两个 HikariCP 池的活跃连接、空闲连接、等待线程、使用率和超时配置，并支持安全模拟占用连接。
- 读写分离思路：`BaseDAO` 默认将查询路由到 `MySQLDBUtil.getReadConnection()`，更新和事务路由到 `MySQLDBUtil.getWriteConnection()`；服务层手写事务也显式使用写连接。
- 数据库连接故障自动重连：获取连接失败或 DAO 捕获连接类 SQL 异常时，会重建对应 HikariCP 读池或写池并重试一次。
- 操作审计日志：关键登录、注册、资料维护、工单创建、状态流转、客服分配、批处理、连接池监控操作写入 MongoDB `system_logs`，同时通过 Logback 写入 `logs/audit.log`；行为日志写入 MongoDB `action_logs` 并同步记录到 `logs/app.log`。

## 构建与运行

```bash
mvn clean test
mvn clean package
java -jar target/ticket-management.jar
```

主类为 `com.ticket.Main`。

桌面启动脚本：

- macOS：双击 `启动工单系统.command`
- Windows：双击 `start-ticket-management.bat`

两个启动脚本都会先用 Maven 打包最新代码，再启动 `target/ticket-management.jar`。Windows 运行前请确认 JDK 17+、Maven 3.8+ 已加入 `PATH`，并已启动 MySQL 与 MongoDB。

初始化数据为每个内置账号设置了不同的临时密码，并在首次登录时强制换密。验收入口：管理员 `admin01 / CedarFalcon#481`，普通用户 `user01 / HarborPine#846`，禁用账号 `user06 / PolarMeadow#769`。其余账号可由管理员在“用户管理”中重置，临时密码仅显示一次。

Day08 压力测试需显式开启：

```bash
mvn -Dstress=true -Dtest=com.ticket.performance.ActionLogStressTest test
```
