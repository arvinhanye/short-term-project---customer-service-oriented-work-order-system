# Ticket Management

面向客服的工单管理系统，基于 Java Swing、MySQL 8、MongoDB 5、JDBC、HikariCP 与 MongoDB Java Sync Driver 实现。

## 当前实现范围

- 需求与数据库设计文档：需求规格说明、用例图、MySQL E-R 图、MongoDB 集合设计
- 冻结数据库脚本：MySQL 表、视图、存储过程、触发器与 MongoDB 初始化脚本
- 工程骨架：`model`、`dto`、`exception`、`dao`、`service`、`ui`
- 用户模块基础能力：注册、登录、资料维护、用户状态控制
- 工单模块基础能力：创建工单、查看我的工单、查看详情、回复、评价、状态流转、客服分配
- 统计模块基础能力：MySQL 存储过程调用、MongoDB 聚合统计、系统日志查询
- Swing 基础工作台：登录页、注册页、普通用户工作台、ADMIN 工作台

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

- [需求规格说明书（含用例图）](/Users/arvinhan/Desktop/2026暑假短学期/ticket-management/docs/requirements.md)
- [数据库设计文档（含 MySQL E-R 图与 MongoDB 集合结构）](/Users/arvinhan/Desktop/2026暑假短学期/ticket-management/docs/database-design.md)
- [Day02 交付记录：数据库实现、工程搭建与 DAO 基础](/Users/arvinhan/Desktop/2026暑假短学期/ticket-management/docs/day02-delivery.md)
- [Day03 交付记录：核心业务模块与 MongoDB DAO](/Users/arvinhan/Desktop/2026暑假短学期/ticket-management/docs/day03-delivery.md)
- [Day04 交付记录：行为日志模块与聚合统计](/Users/arvinhan/Desktop/2026暑假短学期/ticket-management/docs/day04-delivery.md)
- [Day05 交付记录：推荐功能与跨数据库联查](/Users/arvinhan/Desktop/2026暑假短学期/ticket-management/docs/day05-delivery.md)
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

## 配置说明

数据库连接配置位于 [db.properties](/Users/arvinhan/Desktop/2026暑假短学期/ticket-management/src/main/resources/db.properties)：

- `mysql.url`
- `mysql.username`
- `mysql.password`
- `mongodb.uri`
- `mongodb.database`

请根据本机环境修改账号密码后再运行。

## 构建与运行

```bash
mvn clean test
mvn clean package
java -jar target/ticket-management.jar
```

主类为 `com.ticket.Main`。
