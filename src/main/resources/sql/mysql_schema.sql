CREATE DATABASE IF NOT EXISTS ticket_management
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE ticket_management;

SET FOREIGN_KEY_CHECKS = 0;

DROP VIEW IF EXISTS v_business_summary;
DROP VIEW IF EXISTS v_user_detail;

DROP TABLE IF EXISTS system_log_import_records;
DROP TABLE IF EXISTS cross_db_repair_records;
DROP TABLE IF EXISTS pending_mongo_writes;
DROP TABLE IF EXISTS ticket_history;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS profiles;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    role ENUM('ROOT', 'ADMIN', 'USER') NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    must_change_password TINYINT NOT NULL DEFAULT 0,
    password_changed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email),
    INDEX idx_users_role (role),
    INDEX idx_users_status (status),
    INDEX idx_users_role_status (role, status),
    INDEX idx_users_locked_until (locked_until)
);

CREATE TABLE categories (
    category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT NULL,
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories(category_id),
    INDEX idx_categories_parent_id (parent_id)
);

CREATE TABLE items (
    item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    category_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_items_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id),
    INDEX idx_items_category_id (category_id),
    INDEX idx_items_status (status),
    INDEX idx_items_created_at (created_at),
    INDEX idx_items_category_created_at (category_id, created_at),
    FULLTEXT INDEX ft_items_title (title)
);

CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    status TINYINT NOT NULL DEFAULT 0,
    assigned_admin_id BIGINT NULL,
    transfer_request_id CHAR(36) NULL,
    transfer_requested_by BIGINT NULL,
    transfer_target_admin_id BIGINT NULL,
    transfer_reason VARCHAR(200) NULL,
    transfer_requested_at DATETIME(3) NULL,
    reminder_count INT NOT NULL DEFAULT 0,
    last_reminded_at DATETIME(3) NULL,
    workflow_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_orders_item
        FOREIGN KEY (item_id) REFERENCES items(item_id),
    CONSTRAINT fk_orders_assigned_admin
        FOREIGN KEY (assigned_admin_id) REFERENCES users(user_id),
    CONSTRAINT fk_orders_transfer_requester
        FOREIGN KEY (transfer_requested_by) REFERENCES users(user_id),
    CONSTRAINT fk_orders_transfer_target
        FOREIGN KEY (transfer_target_admin_id) REFERENCES users(user_id),
    INDEX idx_orders_user_id (user_id),
    UNIQUE INDEX uk_orders_item_id (item_id),
    INDEX idx_orders_status (status),
    INDEX idx_orders_created_at (created_at),
    INDEX idx_orders_user_created_at (user_id, created_at),
    INDEX idx_orders_user_status_created_at (user_id, status, created_at),
    INDEX idx_orders_status_created_at (status, created_at),
    INDEX idx_orders_assigned_status_created_at (assigned_admin_id, status, created_at),
    INDEX idx_orders_transfer_target_requested_at (transfer_target_admin_id, transfer_requested_at)
);

CREATE TABLE ticket_history (
    history_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    item_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    event_seq BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    visibility ENUM('PUBLIC', 'STAFF_ONLY', 'AUDIT_ONLY') NOT NULL DEFAULT 'STAFF_ONLY',
    actor_user_id BIGINT NULL,
    actor_username VARCHAR(50) NULL,
    actor_role VARCHAR(20) NULL,
    target_user_id BIGINT NULL,
    from_status TINYINT NULL,
    to_status TINYINT NULL,
    from_admin_id BIGINT NULL,
    to_admin_id BIGINT NULL,
    reason VARCHAR(500) NULL,
    source_type VARCHAR(30) NULL,
    source_id VARCHAR(64) NULL,
    event_payload JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_ticket_history_item FOREIGN KEY (item_id) REFERENCES items(item_id),
    CONSTRAINT fk_ticket_history_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
    UNIQUE INDEX uk_ticket_history_event_id (event_id),
    UNIQUE INDEX uk_ticket_history_sequence (item_id, event_seq),
    INDEX idx_ticket_history_item_time (item_id, occurred_at, history_id),
    INDEX idx_ticket_history_actor_time (actor_user_id, occurred_at),
    INDEX idx_ticket_history_type_time (event_type, occurred_at)
);

CREATE TABLE profiles (
    profile_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    real_name VARCHAR(50),
    id_card VARCHAR(20),
    address VARCHAR(500),
    notes TEXT,
    CONSTRAINT fk_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_profiles_user_id UNIQUE (user_id),
    INDEX idx_profiles_user_id (user_id)
);

CREATE TABLE system_log_import_records (
    import_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    log_type VARCHAR(50) NOT NULL,
    log_level VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    ip VARCHAR(64),
    operation VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_import_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_log_import_user_created_at (user_id, created_at),
    INDEX idx_log_import_type_created_at (log_type, created_at),
    INDEX idx_log_import_level_created_at (log_level, created_at)
);

-- MongoDB 暂时不可用时，保留待投递日志，确保业务操作日志最终可补写到 MongoDB。
CREATE TABLE pending_mongo_writes (
    retry_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    write_type ENUM('ACTION', 'SYSTEM') NOT NULL,
    user_id VARCHAR(64),
    item_id VARCHAR(64),
    log_type VARCHAR(64) NOT NULL,
    log_level VARCHAR(20),
    message TEXT,
    operation VARCHAR(200),
    client_type VARCHAR(50),
    ip VARCHAR(64),
    occurred_at DATETIME NOT NULL,
    status ENUM('PENDING', 'DONE') NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pending_mongo_status_created_at (status, created_at)
);

-- 跨库补偿删除失败时的持久化修复队列。
CREATE TABLE cross_db_repair_records (
    repair_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    repair_type ENUM('DELETE_ITEM_DETAIL') NOT NULL,
    item_id BIGINT NOT NULL,
    status ENUM('PENDING', 'DONE') NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cross_db_repair_status_created_at (status, created_at)
);
