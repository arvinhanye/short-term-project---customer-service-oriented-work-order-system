# Day02 交付记录：数据库实现、工程搭建与 DAO 基础

## 1. 交付目标

根据 Day02 要求，本阶段完成数据库脚本整理、Maven 工程基础确认、DAO 基础能力完善，以及 `UserDAO` 完整 CRUD 与单元测试验证。

## 2. 数据库脚本

全部数据库脚本位于 `src/main/resources/sql/`：

| 脚本 | 内容 |
| --- | --- |
| `mysql_schema.sql` | MySQL 建库、建表、索引与外键 |
| `mysql_views.sql` | 用户详情视图、业务汇总视图 |
| `mysql_procedures.sql` | 月度报表、批量状态更新存储过程 |
| `mysql_triggers.sql` | 工单状态同步、更新时间维护触发器 |
| `mysql_init_data.sql` | MySQL 测试初始化数据 |
| `mongodb_init.js` | MongoDB 集合索引与初始化数据 |

推荐执行顺序：

```bash
mysql -u root -p < src/main/resources/sql/mysql_schema.sql
mysql -u root -p < src/main/resources/sql/mysql_views.sql
mysql -u root -p < src/main/resources/sql/mysql_procedures.sql
mysql -u root -p < src/main/resources/sql/mysql_triggers.sql
mysql -u root -p < src/main/resources/sql/mysql_init_data.sql
mongosh < src/main/resources/sql/mongodb_init.js
```

## 3. 工程结构

项目采用 Maven 标准结构：

```text
src/main/java/com/ticket/
├── config      数据库配置读取
├── dao         BaseDAO、MongoBaseDAO 与 MySQL/MongoDB DAO
├── dto         页面与报表传输对象
├── exception   业务异常与数据库异常
├── model       用户、工单、分类、日志等模型
├── service     用户、工单、推荐、统计业务服务
├── ui          Swing 登录、注册与工作台界面
└── util        MySQL、MongoDB、密码工具类
```

## 4. DAO 完成情况

### 4.1 BaseDAO

- 保留默认 MySQL 数据源行为。
- 增加可选 `DataSource` 构造器，便于 DAO 单元测试使用内存数据库。
- 提供通用 `query`、`queryOne`、`update` 和事务回调封装。

### 4.2 UserDAO

已完成用户表完整 CRUD 与常用查询：

- `insert(User user)`
- `insert(Connection connection, User user)`
- `findById(Long userId)`
- `findByUsername(String username)`
- `findByEmail(String email)`
- `findAll()`
- `findByRole(String role)`
- `findByStatus(int status)`
- `update(User user)`
- `updateBasicInfo(User user)`
- `updatePasswordHash(Long userId, String passwordHash)`
- `updateStatus(Long userId, int status)`
- `deleteById(Long userId)`

## 5. 单元测试

新增 `src/test/java/com/ticket/dao/mysql/UserDAOTest.java`，使用 H2 内存数据库模拟 `users` 表，验证 `UserDAO` 完整 CRUD，不依赖本机 MySQL 服务。

验证命令：

```bash
mvn test
```

验证结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 6. Day02 提交记录

建议提交信息：

```text
Day02 database and UserDAO foundation
```

本次提交内容覆盖：

- 全部数据库脚本确认。
- Maven 测试依赖补充。
- `BaseDAO` 测试友好改造。
- `UserDAO` 完整 CRUD。
- `UserDAO` 单元测试。
- Day02 交付记录文档。
