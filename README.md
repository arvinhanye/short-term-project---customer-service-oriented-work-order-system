# Ticket Management

面向客服的工单管理系统，基于 Java Swing、MySQL 8、MongoDB 5、JDBC、HikariCP 与 MongoDB Java Sync Driver 实现。

## 当前实现范围

- 需求与数据库设计文档：需求规格说明、用例图、MySQL E-R 图、MongoDB 集合设计
- 冻结数据库脚本：MySQL 表、视图、存储过程、触发器与 MongoDB 初始化脚本
- 工程骨架：`model`、`dto`、`exception`、`dao`、`service`、`ui`
- 用户模块基础能力：注册、登录、资料维护、用户状态控制
- 工单模块基础能力：创建工单、查看我的工单、查看详情、回复、催促、评价、状态流转、认领与双向确认转派
- 统计报表模块：MySQL 月度报表存储过程调用、MongoDB 聚合统计、系统日志审计查询
- 稳定版加固：分页 SQL 优化、性能索引、批处理状态流转、密码强度校验、系统自检
- 测试与重构：JUnit5 单元测试、事务回滚测试、行为日志压力测试入口、公共日志服务与分类服务分层重构
- Swing 人性化工作台：统一主题、登录注册、普通用户工单中心、ROOT/ADMIN/USER 三层权限、状态标签和异步加载反馈
- 加分项增强：连接池监控面板、MySQL 读写分离连接池、连接故障自动重连、操作审计日志落库与落盘

## 本次维护更新

- 工单列表改为 MySQL 联表摘要查询，并批量补充 MongoDB 工单详情，避免列表逐行跨库查询。
- 普通用户“我的工单”改用基于“创建时间 + 记录编号”的游标分页；筛选条件变化或退出登录时会重置分页游标和会话缓存。
- MongoDB 行为、评论和审计聚合默认统计近 30 天数据，排行结果默认最多展示 20 条，降低历史日志增长对桌面端查询的影响。
- 管理员工单、用户、自检和统计查询改为后台异步加载，避免数据库访问阻塞 Swing 事件线程。
- UI 按 P0-P3 完成改造：统一视觉规范，减少空白和分散入口；管理员工作概览提供个人状态环形图、待办风险列表和“我的工单”快捷筛选，收到的转派会以第六种展示状态“待确认”进入指标、环形图和风险首位，点击可直接处理；创建工单采用紧凑双栏布局；管理员用户管理支持筛选、启用与禁用账号；主要弹窗支持 Esc 关闭。
- 分类管理升级为“统计卡 + 可搜索分类树 + 详情编辑”界面：直接显示层级、子分类数、直接/总关联工单和待治理标记；保存失败保留输入，删除前明确展示影响范围。
- 分类结构严格限制为两级，服务层阻止三级分类、非法降级和同层重名；用户端分类选择、工单详情与推荐结果显示完整分类路径。
- 用户管理改为“顶部筛选 + 左侧账号列表 + 右侧账号详情与状态操作”，提供筛选重置、启用/禁用数量和当前账号保护；自我禁用与不存在用户校验同步下沉至 `UserService`。
- 统计报表改为月度指标卡、顶部行为统计口径和分行日志筛选/快速汇总；动态 MongoDB 字段统一显示中文表头，空结果、加载状态和页签错误提示互不串扰。
- 系统自检改为独立窗口，通过状态卡和明细表展示五项检查的结果、单项耗时与失败原因，支持安全重复执行和关闭窗口时取消后台任务。
- 连接池监控改为 READ/WRITE 双状态卡、实时对比表和参数页签；自动刷新移至后台并防止任务重叠，模拟占用会根据当前负载缩减连接数并保留业务余量。
- 异步可靠性修复：后台任务绑定登录会话和请求序号，过期结果不会覆盖新账号或新筛选；管理员工单支持完整分页，资料读写不再阻塞 Swing 事件线程。
- 权限模型收敛为 `ROOT > ADMIN > USER`：原 AGENT 账号无损迁移为 ADMIN，工单处理统一由 ADMIN 承担，ROOT 继续负责账号治理。
- 用户管理列表不再读取密码哈希；可分配管理员由数据库直接按角色和启用状态筛选，减少全表传输和 Java 端重复过滤。
- 管理员工单分页复用已加载的分类路径映射，避免每次翻页或调整筛选条件都重复查询分类表。
- 新建工单默认进入“未分配池”；ADMIN 可确认认领，或向其他 ADMIN 发起接手邀请。跨管理员转派必须填写原因并经目标管理员接受，在接受前原负责人不变。
- 未认领工单不能回复、写内部备注或流转状态；状态按“待处理 → 处理中 → 已完成 → 已关闭”逐步流转，待处理/处理中可取消，并使用乐观锁防止并发覆盖。
- 普通用户可对本人待处理或处理中工单发起催促，同一工单 30 分钟内最多一次；管理员列表和详情同步显示催促次数与时间。
- 新增 MySQL 追加式 `ticket_history`：创建、认领、转派申请/接受/拒绝/撤销、回复、内部备注、催促、评价、状态变化和自动批处理均按工单版本留痕；普通用户只看到公开进度，ADMIN 可查看完整团队协作时间线。
- 当前负责人、待确认转派、催促次数和工作流版本以 MySQL `orders` 为权威数据，MongoDB 只保留兼容镜像；业务变更与历史事件在同一 MySQL 事务内提交。

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

旧版本数据库必须执行 `src/main/resources/sql/mysql_role_governance.sql` 和 `src/main/resources/sql/mongodb_role_governance.js`。前者将现有 AGENT 无损迁移为 ADMIN，并把角色枚举收敛为 `ROOT/ADMIN/USER`；后者只把历史客服回复标签改名为 `ADMIN_REPLY`。两者都不会重置密码或删除账号、工单、评论及其他关联数据。

旧版本数据库还必须执行 `src/main/resources/sql/mysql_ticket_history.sql`。该增量脚本只给 `orders` 增加工作流字段、创建 `ticket_history`、索引和防篡改触发器，不会清空原有测试数据；应用首次启动会从 MongoDB 当前状态回填每张旧工单的 `MIGRATION_SNAPSHOT`，无法可靠还原的迁移前过程不会伪造。

若旧测试数据仍包含历史三级分类 `#4006`，在工单历史脚本之后执行 `src/main/resources/sql/mysql_category_cleanup.sql`。脚本会在同一事务内先为关联工单写入 `CATEGORY_REASSIGNED` 历史、迁移至合法父分类 `#4007`，再删除已经为空的异常分类；不会删除工单，可安全重复执行。

需要进行完整角色与工单流程展示时，可依次执行 `src/main/resources/sql/mysql_demo_scenarios.sql` 和 `src/main/resources/sql/mongodb_demo_scenarios.js`。两份脚本会追加 ROOT、ADMIN、USER 三个专用演示账号以及 60 张真实化工单，不删除原有数据，并可重复执行。账号、密码和覆盖矩阵见 [三角色演示数据说明](docs/demo-test-accounts.md)。

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

启动脚本会确保使用最新的 `target/ticket-management.jar`；macOS 只在源码或配置变化时重新打包，后续窗口直接复用 JAR。Windows 运行前请确认 JDK 17+、Maven 3.8+ 已加入 `PATH`，并已启动 MySQL 与 MongoDB。

启动器支持多实例演示：已有窗口运行时再次双击启动脚本，会启动新的独立 Java 进程；每个标题栏带有窗口编号，可分别登录 ROOT、两个 ADMIN 和 USER。所有窗口共享数据库，但登录会话、连接池、后台任务和退出操作相互独立。

初始化数据为每个内置账号设置了不同的临时密码，并在首次登录时强制换密。三角色验收入口：ROOT `admin01 / CedarFalcon#481`、ADMIN `admin03 / BusinessAdmin#583`（备用 ADMIN：`admin04 / ServiceAgent#742`）、USER `user01 / HarborPine#846`。后台账号仅由 ROOT 在“权限管理”中创建或调整，临时密码只显示一次。

Day08 压力测试需显式开启：

```bash
mvn -Dstress=true -Dtest=com.ticket.performance.ActionLogStressTest test
```
