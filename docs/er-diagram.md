# 工单管理系统 E-R 图

本文档使用 Mermaid `erDiagram` 语法生成，可直接在 GitHub、GitLab、Typora、Obsidian 或支持 Mermaid 的 Markdown 编辑器中预览。

> 数据来源：`src/main/resources/sql/mysql_schema.sql`、`mongodb_init.js`、`mongodb_attachments.js` 和 `mongodb_p1_lifecycle.js`。图中的 MySQL 关系均为真实外键；跨库关系为应用层逻辑关联，不是数据库外键。

## 1. 核心业务 E-R 图

```mermaid
erDiagram
    users ||--o| profiles : 拥有
    users ||--o{ orders : 提交
    users o|--o{ orders : 负责
    categories o|--o{ categories : 包含子分类
    categories ||--o{ items : 分类
    items ||--o| orders : 对应处理单
    sla_policies o|--o{ orders : 约束
    items ||--o{ ticket_history : 产生历史
    orders o|--o{ ticket_history : 记录版本
    users ||--o{ notifications : 接收
    items o|--o{ notifications : 关联
    users ||--o{ ticket_ratings : 评价
    items ||--o{ ticket_ratings : 获得评价
    categories o|--o{ ticket_assignment_rules : 匹配
    users o|--o{ ticket_assignment_rules : 指定管理员
    categories o|--o{ knowledge_articles : 归类
    users ||--o{ knowledge_articles : 维护
    categories o|--o{ reply_templates : 适用
    reply_templates o|--o{ handling_macros : 引用
```

## 2. MySQL 完整 E-R 图

该图只绘制数据库实际声明的外键。`ticket_history` 中的操作者、目标用户和负责人字段是历史快照，不声明外键，以免账号变更影响历史解释。

```mermaid
erDiagram
    users ||--o| profiles : has
    users ||--o{ orders : submits
    users o|--o{ orders : assigned_admin
    users o|--o{ orders : transfer_requester
    users o|--o{ orders : transfer_target
    users ||--o{ notifications : receives
    users ||--o{ ticket_ratings : rates
    users o|--o{ ticket_assignment_rules : target_admin
    users ||--o{ knowledge_articles : creates
    users ||--o{ knowledge_articles : updates
    users ||--o{ reply_templates : creates
    users ||--o{ handling_macros : creates
    users ||--o{ data_lifecycle_runs : performs
    users o|--o{ system_log_import_records : imports

    categories o|--o{ categories : parent_of
    categories ||--o{ items : classifies
    categories o|--o{ ticket_assignment_rules : matches
    categories o|--o{ knowledge_articles : groups
    categories o|--o{ reply_templates : scopes

    items ||--o| orders : owns
    items o|--o{ notifications : referenced_by
    items ||--o{ ticket_ratings : receives
    items ||--o{ ticket_history : records

    sla_policies o|--o{ orders : governs
    orders o|--o{ ticket_history : versions
    reply_templates o|--o{ handling_macros : used_by

    users {
        BIGINT user_id PK
        VARCHAR username UK
        VARCHAR password_hash
        VARCHAR email UK
        VARCHAR phone
        ENUM role
        TINYINT status
        INT failed_login_attempts
        DATETIME locked_until
        TINYINT must_change_password
        DATETIME password_changed_at
        DATETIME created_at
        DATETIME updated_at
    }

    profiles {
        BIGINT profile_id PK
        BIGINT user_id FK, UK
        VARCHAR real_name
        VARCHAR id_card
        VARCHAR address
        TEXT notes
        VARCHAR notification_preference
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

    sla_policies {
        BIGINT policy_id PK
        VARCHAR policy_name
        VARCHAR priority UK
        INT first_response_minutes
        INT next_response_minutes
        INT resolution_minutes
        TINYINT business_hours_only
        TINYINT enabled
        DATETIME created_at
        DATETIME updated_at
    }

    orders {
        BIGINT order_id PK
        BIGINT user_id FK
        BIGINT item_id FK, UK
        DECIMAL amount
        TINYINT status
        BIGINT assigned_admin_id FK
        CHAR transfer_request_id
        BIGINT transfer_requested_by FK
        BIGINT transfer_target_admin_id FK
        VARCHAR transfer_reason
        DATETIME transfer_requested_at
        INT reminder_count
        DATETIME last_reminded_at
        BIGINT sla_policy_id FK
        DATETIME first_response_due_at
        DATETIME next_response_due_at
        DATETIME resolution_due_at
        DATETIME first_responded_at
        DATETIME last_admin_response_at
        DATETIME resolved_at
        ENUM sla_state
        DATETIME sla_paused_at
        VARCHAR sla_pause_reason
        INT total_sla_paused_minutes
        DATETIME reopen_deadline_at
        INT reopen_count
        BIGINT workflow_version
        DATETIME created_at
    }

    ticket_assignment_rules {
        BIGINT rule_id PK
        VARCHAR rule_name
        BIGINT category_id FK
        VARCHAR priority
        ENUM strategy
        BIGINT target_admin_id FK
        TINYINT enabled
        INT sort_order
        DATETIME created_at
        DATETIME updated_at
    }

    notifications {
        BIGINT notification_id PK
        BIGINT user_id FK
        BIGINT item_id FK
        VARCHAR notification_type
        VARCHAR title
        VARCHAR content
        VARCHAR dedup_key
        DATETIME read_at
        DATETIME deleted_at
        DATETIME created_at
    }

    ticket_ratings {
        BIGINT rating_id PK
        BIGINT item_id FK
        BIGINT user_id FK
        CHAR event_id UK
        TINYINT rating
        DATETIME created_at
    }

    knowledge_articles {
        BIGINT article_id PK
        VARCHAR title
        VARCHAR summary
        MEDIUMTEXT content
        BIGINT category_id FK
        VARCHAR keywords
        ENUM status
        BIGINT created_by FK
        BIGINT updated_by FK
        DATETIME created_at
        DATETIME updated_at
    }

    reply_templates {
        BIGINT template_id PK
        VARCHAR template_name
        TEXT content
        BIGINT category_id FK
        TINYINT enabled
        BIGINT created_by FK
        DATETIME created_at
        DATETIME updated_at
    }

    handling_macros {
        BIGINT macro_id PK
        VARCHAR macro_name
        BIGINT reply_template_id FK
        TINYINT target_status
        TINYINT enabled
        BIGINT created_by FK
        DATETIME created_at
        DATETIME updated_at
    }

    data_lifecycle_runs {
        BIGINT run_id PK
        VARCHAR run_type
        DATETIME cutoff_at
        BIGINT affected_count
        VARCHAR artifact_path
        CHAR artifact_checksum
        ENUM result_status
        VARCHAR result_message
        BIGINT performed_by FK
        DATETIME created_at
    }

    ticket_history {
        BIGINT history_id PK
        CHAR event_id UK
        BIGINT item_id FK
        BIGINT order_id FK
        BIGINT event_seq
        VARCHAR event_type
        ENUM visibility
        BIGINT actor_user_id
        VARCHAR actor_username
        VARCHAR actor_role
        BIGINT target_user_id
        TINYINT from_status
        TINYINT to_status
        BIGINT from_admin_id
        BIGINT to_admin_id
        VARCHAR reason
        VARCHAR source_type
        VARCHAR source_id
        JSON event_payload
        DATETIME occurred_at
        DATETIME created_at
    }

    system_log_import_records {
        BIGINT import_id PK
        BIGINT user_id FK
        VARCHAR log_type
        VARCHAR log_level
        VARCHAR message
        VARCHAR ip
        VARCHAR operation
        DATETIME created_at
    }

    pending_mongo_writes {
        BIGINT retry_id PK
        ENUM write_type
        VARCHAR user_id
        VARCHAR item_id
        VARCHAR log_type
        VARCHAR log_level
        TEXT message
        VARCHAR operation
        VARCHAR client_type
        VARCHAR ip
        DATETIME occurred_at
        ENUM status
        INT attempt_count
        VARCHAR last_error
        DATETIME created_at
        DATETIME updated_at
    }

    cross_db_repair_records {
        BIGINT repair_id PK
        ENUM repair_type
        BIGINT item_id
        ENUM status
        INT attempt_count
        VARCHAR last_error
        DATETIME created_at
        DATETIME updated_at
    }
```

`pending_mongo_writes` 是 MongoDB 写入失败后的重试队列；`cross_db_repair_records.item_id` 是逻辑关联字段。两张表未声明外键，因此在图中保持独立。

Mermaid 不能直接标注复合唯一键：`notifications` 使用 `(user_id, dedup_key)`，`ticket_ratings` 使用 `(item_id, user_id)`，`ticket_history` 使用 `(item_id, event_seq)`。

## 3. MongoDB 与跨库逻辑关系图

以下连线表示 Java 应用维护的逻辑关联。MongoDB 中的 `user_id`、`item_id` 使用字符串保存，与 MySQL 的 `BIGINT` 主键通过字符串转换对应。

```mermaid
erDiagram
    MYSQL_USERS ||--o{ MONGO_COMMENTS : user_id
    MYSQL_ITEMS ||--o| MONGO_ITEM_DETAILS : item_id
    MYSQL_ITEMS ||--o{ MONGO_COMMENTS : item_id
    MYSQL_USERS o|--o{ MONGO_ACTION_LOGS : user_id
    MYSQL_ITEMS o|--o{ MONGO_ACTION_LOGS : item_id
    MYSQL_USERS o|--o{ MONGO_SYSTEM_LOGS : user_id
    MYSQL_TICKET_HISTORY o|--o| MONGO_COMMENTS : source_id_event_id
    MYSQL_TICKET_RATINGS o|--o| MONGO_COMMENTS : event_id
    MONGO_COMMENTS ||--o{ GRIDFS_FILES : attachments
    GRIDFS_FILES ||--|{ GRIDFS_CHUNKS : files_id
    MONGO_ACTION_LOGS ||--o{ MONGO_ACTION_LOGS_ARCHIVE : lifecycle_archive
    MONGO_SYSTEM_LOGS ||--o{ MONGO_SYSTEM_LOGS_ARCHIVE : lifecycle_archive

    MYSQL_USERS {
        BIGINT user_id PK
        VARCHAR username
        ENUM role
    }

    MYSQL_ITEMS {
        BIGINT item_id PK
        VARCHAR title
        BIGINT category_id FK
        TINYINT status
    }

    MYSQL_TICKET_HISTORY {
        BIGINT history_id PK
        CHAR event_id UK
        BIGINT item_id FK
        VARCHAR source_id
        VARCHAR source_type
    }

    MYSQL_TICKET_RATINGS {
        BIGINT rating_id PK
        CHAR event_id UK
        BIGINT item_id FK
        BIGINT user_id FK
        TINYINT rating
    }

    MONGO_ITEM_DETAILS {
        OBJECT_ID _id PK
        STRING item_id UK
        STRING description
        ARRAY images
        OBJECT metadata
    }

    MONGO_COMMENTS {
        OBJECT_ID _id PK
        STRING event_id UK
        STRING user_id
        STRING item_id
        STRING content
        STRING sticker_code
        STRING rating
        ARRAY tags
        ARRAY attachments
        DATE created_at
    }

    MONGO_ACTION_LOGS {
        OBJECT_ID _id PK
        STRING user_id
        STRING item_id
        STRING action_type
        STRING duration_seconds
        OBJECT client_info
        DATE created_at
    }

    MONGO_SYSTEM_LOGS {
        OBJECT_ID _id PK
        STRING user_id
        STRING log_type
        STRING log_level
        STRING message
        OBJECT action_detail
        DATE timestamp
    }

    GRIDFS_FILES {
        OBJECT_ID _id PK
        STRING filename
        LONG length
        INT chunkSize
        DATE uploadDate
        OBJECT metadata
    }

    GRIDFS_CHUNKS {
        OBJECT_ID _id PK
        OBJECT_ID files_id FK
        INT n
        BINARY data
    }

    MONGO_ACTION_LOGS_ARCHIVE {
        OBJECT_ID _id PK
        DATE created_at
        DATE archived_at
    }

    MONGO_SYSTEM_LOGS_ARCHIVE {
        OBJECT_ID _id PK
        DATE timestamp
        DATE archived_at
    }
```

## 4. 关系基数说明

| 符号 | 含义 |
| --- | --- |
| `||` | 必须且仅有一个 |
| `o|` | 零个或一个 |
| `|{` | 一个或多个 |
| `o{` | 零个或多个 |
