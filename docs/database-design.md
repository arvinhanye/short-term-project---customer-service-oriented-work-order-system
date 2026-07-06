# 数据库设计文档

## 1. 设计概述

系统采用 MySQL + MongoDB 的组合存储方案：

- MySQL 保存用户、分类、工单主表、工单处理记录和用户资料等结构化数据。
- MongoDB 保存工单详情、评论、行为日志和系统日志等半结构化或高增长数据。
- Java DAO 层分别通过 JDBC/HikariCP 和 MongoDB Java Sync Driver 访问两类数据库。

## 2. MySQL E-R 图

```mermaid
erDiagram
    users ||--o| profiles : has
    users ||--o{ orders : submits
    categories ||--o{ categories : parent
    categories ||--o{ items : classifies
    items ||--|| orders : owns

    users {
        BIGINT user_id PK
        VARCHAR username UK
        VARCHAR password_hash
        VARCHAR email UK
        VARCHAR phone
        ENUM role
        TINYINT status
        DATETIME created_at
        DATETIME updated_at
    }

    profiles {
        BIGINT profile_id PK
        BIGINT user_id FK,UK
        VARCHAR real_name
        VARCHAR id_card
        VARCHAR address
        TEXT notes
    }

    categories {
        BIGINT category_id PK
        VARCHAR name
        BIGINT parent_id FK
    }

    items {
        BIGINT item_id PK
        VARCHAR title
        BIGINT category_id FK
        TINYINT status
        DATETIME created_at
        DATETIME updated_at
    }

    orders {
        BIGINT order_id PK
        BIGINT user_id FK
        BIGINT item_id FK
        DECIMAL amount
        TINYINT status
        DATETIME created_at
    }
```

## 3. MySQL 表结构说明

### 3.1 users

用户账号表，用于保存登录凭证、联系方式、角色和账号状态。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| user_id | BIGINT | PK, AUTO_INCREMENT | 用户编号 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 用户名 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt 密码哈希 |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 邮箱 |
| phone | VARCHAR(20) |  | 手机号 |
| role | ENUM('ADMIN','USER') | NOT NULL | 用户角色 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1 启用，0 禁用 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

### 3.2 profiles

用户资料表，与 `users` 一对一。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| profile_id | BIGINT | PK, AUTO_INCREMENT | 资料编号 |
| user_id | BIGINT | FK, UNIQUE | 用户编号 |
| real_name | VARCHAR(50) |  | 真实姓名 |
| id_card | VARCHAR(20) |  | 身份证号 |
| address | VARCHAR(500) |  | 地址 |
| notes | TEXT |  | 备注 |

### 3.3 categories

工单分类表，支持父子分类。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| category_id | BIGINT | PK, AUTO_INCREMENT | 分类编号 |
| name | VARCHAR(50) | NOT NULL | 分类名称 |
| parent_id | BIGINT | FK | 父分类编号 |

### 3.4 items

工单主表，保存工单标题、分类和可见状态。代码中沿用 `Item` 命名，业务语义为工单主记录。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| item_id | BIGINT | PK, AUTO_INCREMENT | 工单编号 |
| title | VARCHAR(200) | NOT NULL | 工单标题 |
| category_id | BIGINT | FK | 分类编号 |
| status | TINYINT | NOT NULL, DEFAULT 1 | 主记录状态 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

### 3.5 orders

工单处理记录表，保存提交人、金额和业务状态。代码中沿用 `Order` 命名，业务语义为工单流转记录。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| order_id | BIGINT | PK, AUTO_INCREMENT | 处理记录编号 |
| user_id | BIGINT | FK | 提交用户 |
| item_id | BIGINT | FK | 工单编号 |
| amount | DECIMAL(10,2) | NOT NULL | 涉及金额 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0 待处理，1 处理中，2 已完成，3 已关闭，4 已取消 |
| created_at | DATETIME | NOT NULL | 提交时间 |

## 4. MySQL 视图、过程与触发器

| 对象 | 说明 |
| --- | --- |
| v_user_detail | 联合用户账号和资料，便于管理员查看完整用户信息 |
| v_business_summary | 联合工单、分类、提交人和处理状态，便于业务列表展示 |
| sp_monthly_report | 按年月统计工单总量、状态数量、总金额和平均金额 |
| sp_batch_update_order_status | 按时间批量更新指定状态的工单 |
| trg_order_status_sync | 工单状态变更后同步更新工单主记录更新时间 |
| trg_item_update_time | 工单主记录更新前自动刷新 `updated_at` |

## 5. MongoDB 集合设计

MongoDB 数据库名：`ticket_management_logs`。

### 5.1 item_details

保存工单长文本详情、附件地址和处理元数据。`item_id` 建唯一索引，与 MySQL `items.item_id` 形成逻辑一对一关系。

```json
{
  "item_id": "2001",
  "description": "工单详细描述、复现步骤和业务背景",
  "images": ["/attachments/2001/screenshot-1.png"],
  "metadata": {
    "language": "zh-CN",
    "priority": "HIGH",
    "created_by_user_id": "10004",
    "assigned_admin_id": "10001",
    "contact_channel": "DESKTOP",
    "last_processed_at": "2026-03-01T09:00:00Z"
  }
}
```

索引：

| 索引 | 说明 |
| --- | --- |
| `{ item_id: 1 }` unique | 按工单编号快速读取详情，保证一个工单一份详情 |

### 5.2 comments

保存工单回复、内部备注和用户评价。

```json
{
  "user_id": "10004",
  "item_id": "2001",
  "content": "用户补充说明或客服回复内容",
  "rating": "5",
  "tags": ["CUSTOMER_RATING"],
  "created_at": "2026-04-01T08:00:00Z"
}
```

索引：

| 索引 | 说明 |
| --- | --- |
| `{ item_id: 1, created_at: 1 }` | 按工单时间线读取评论 |
| `{ user_id: 1 }` | 查询某用户评论 |
| `{ tags: 1 }` | 区分客户回复、客服回复、内部备注和评价 |

### 5.3 action_logs

保存用户行为日志，用于热门工单和用户行为统计。

```json
{
  "user_id": "10004",
  "item_id": "2001",
  "action_type": "CREATE_ITEM",
  "duration_seconds": "30",
  "client_info": {
    "client_type": "SWING",
    "ip": "127.0.0.1"
  },
  "created_at": "2026-05-01T07:00:00Z"
}
```

索引：

| 索引 | 说明 |
| --- | --- |
| `{ user_id: 1 }` | 按用户分析行为 |
| `{ item_id: 1 }` | 按工单统计热度 |
| `{ action_type: 1 }` | 按行为类型聚合 |
| `{ created_at: 1 }` | 按时间范围筛选 |

### 5.4 system_logs

保存登录、异常、状态变更和管理员操作等系统审计日志。

```json
{
  "user_id": "10001",
  "log_type": "STATUS_CHANGE",
  "log_level": "INFO",
  "message": "工单状态已更新",
  "action_detail": {
    "ip": "127.0.0.1",
    "operation": "CHANGE_STATUS"
  },
  "timestamp": "2026-06-01T06:00:00Z"
}
```

索引：

| 索引 | 说明 |
| --- | --- |
| `{ user_id: 1 }` | 按用户过滤审计日志 |
| `{ log_type: 1 }` | 按日志类型聚合 |
| `{ log_level: 1 }` | 按日志等级筛选 |
| `{ timestamp: -1 }` | 最近日志倒序查询 |

## 6. 跨库关系

| MySQL 对象 | MongoDB 对象 | 关联字段 | 说明 |
| --- | --- | --- | --- |
| items | item_details | `items.item_id` = `item_details.item_id` | 工单主记录与详情一对一 |
| items | comments | `items.item_id` = `comments.item_id` | 工单与回复一对多 |
| users | comments | `users.user_id` = `comments.user_id` | 用户与回复一对多 |
| users/items | action_logs | `user_id`、`item_id` | 行为日志引用用户和工单 |
| users | system_logs | `users.user_id` = `system_logs.user_id` | 系统日志引用用户 |

## 7. 初始化脚本

按以下顺序执行：

1. `src/main/resources/sql/mysql_schema.sql`
2. `src/main/resources/sql/mysql_views.sql`
3. `src/main/resources/sql/mysql_procedures.sql`
4. `src/main/resources/sql/mysql_triggers.sql`
5. `src/main/resources/sql/mysql_init_data.sql`
6. `src/main/resources/sql/mongodb_init.js`
