USE ticket_management;

-- SLA、站内通知、自动分配和唯一评价增量升级。执行前请备份数据库。
CREATE TABLE IF NOT EXISTS sla_policies (
    policy_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_name VARCHAR(80) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    first_response_minutes INT NOT NULL,
    next_response_minutes INT NOT NULL,
    resolution_minutes INT NOT NULL,
    business_hours_only TINYINT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_sla_policy_priority (priority),
    INDEX idx_sla_policy_enabled (enabled)
);

INSERT INTO sla_policies (policy_name, priority, first_response_minutes, next_response_minutes,
                          resolution_minutes, business_hours_only, enabled) VALUES
    ('低优先级服务标准', 'LOW', 480, 480, 2400, 1, 1),
    ('普通服务标准', 'MEDIUM', 240, 240, 1440, 1, 1),
    ('高优先级服务标准', 'HIGH', 120, 120, 480, 1, 1),
    ('紧急服务标准', 'URGENT', 30, 60, 240, 1, 1)
ON DUPLICATE KEY UPDATE policy_name = VALUES(policy_name), enabled = VALUES(enabled);

DROP PROCEDURE IF EXISTS add_ticket_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_ticket_column_if_missing(IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE orders ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END $$
DELIMITER ;

CALL add_ticket_column_if_missing('sla_policy_id', 'BIGINT NULL AFTER last_reminded_at');
CALL add_ticket_column_if_missing('first_response_due_at', 'DATETIME(3) NULL AFTER sla_policy_id');
CALL add_ticket_column_if_missing('next_response_due_at', 'DATETIME(3) NULL AFTER first_response_due_at');
CALL add_ticket_column_if_missing('resolution_due_at', 'DATETIME(3) NULL AFTER next_response_due_at');
CALL add_ticket_column_if_missing('first_responded_at', 'DATETIME(3) NULL AFTER resolution_due_at');
CALL add_ticket_column_if_missing('last_admin_response_at', 'DATETIME(3) NULL AFTER first_responded_at');
CALL add_ticket_column_if_missing('resolved_at', 'DATETIME(3) NULL AFTER last_admin_response_at');
CALL add_ticket_column_if_missing('sla_state',
    'ENUM(''ACTIVE'', ''MET'', ''BREACHED'', ''CANCELLED'') NULL AFTER resolved_at');
DROP PROCEDURE add_ticket_column_if_missing;

DROP PROCEDURE IF EXISTS add_ticket_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_ticket_index_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_sla_state_due'
    ) THEN
        CREATE INDEX idx_orders_sla_state_due
            ON orders (sla_state, first_response_due_at, next_response_due_at, resolution_due_at);
    END IF;
END $$
DELIMITER ;
CALL add_ticket_index_if_missing();
DROP PROCEDURE add_ticket_index_if_missing;

DROP PROCEDURE IF EXISTS add_ticket_sla_fk_if_missing;
DELIMITER $$
CREATE PROCEDURE add_ticket_sla_fk_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.referential_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'orders'
          AND constraint_name = 'fk_orders_sla_policy'
    ) THEN
        ALTER TABLE orders ADD CONSTRAINT fk_orders_sla_policy
            FOREIGN KEY (sla_policy_id) REFERENCES sla_policies(policy_id);
    END IF;
END $$
DELIMITER ;
CALL add_ticket_sla_fk_if_missing();
DROP PROCEDURE add_ticket_sla_fk_if_missing;

DROP PROCEDURE IF EXISTS add_profile_notification_preference;
DELIMITER $$
CREATE PROCEDURE add_profile_notification_preference()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'profiles'
          AND column_name = 'notification_preference'
    ) THEN
        ALTER TABLE profiles ADD COLUMN notification_preference VARCHAR(20) NOT NULL DEFAULT 'ALL' AFTER notes;
    END IF;
END $$
DELIMITER ;
CALL add_profile_notification_preference();
DROP PROCEDURE add_profile_notification_preference;

UPDATE profiles
SET notification_preference = CASE
    WHEN notes LIKE '%通知偏好：不通知%' THEN 'NONE'
    WHEN notes LIKE '%通知偏好：仅处理结果%' THEN 'RESULT'
    WHEN notes LIKE '%通知偏好：状态变更%' THEN 'STATUS'
    ELSE 'ALL'
END
WHERE notes LIKE '%通知偏好：%';

CREATE TABLE IF NOT EXISTS ticket_assignment_rules (
    rule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_name VARCHAR(100) NOT NULL,
    category_id BIGINT NULL,
    priority VARCHAR(20) NULL,
    strategy ENUM('SPECIFIC_ADMIN', 'LEAST_LOADED') NOT NULL,
    target_admin_id BIGINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 100,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignment_rule_category FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_rule_admin FOREIGN KEY (target_admin_id) REFERENCES users(user_id),
    INDEX idx_assignment_rules_match (enabled, category_id, priority, sort_order)
);

INSERT INTO ticket_assignment_rules (rule_name, category_id, priority, strategy,
                                     target_admin_id, enabled, sort_order)
SELECT '全部工单按最少待办自动分配', NULL, NULL, 'LEAST_LOADED', NULL, 1, 1000
WHERE NOT EXISTS (SELECT 1 FROM ticket_assignment_rules);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    item_id BIGINT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(500) NOT NULL,
    dedup_key VARCHAR(160) NULL,
    read_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_notifications_item FOREIGN KEY (item_id) REFERENCES items(item_id),
    UNIQUE INDEX uk_notifications_user_dedup (user_id, dedup_key),
    INDEX idx_notifications_user_read_time (user_id, read_at, created_at)
);

DROP PROCEDURE IF EXISTS add_notification_deleted_at_if_missing;
DELIMITER $$
CREATE PROCEDURE add_notification_deleted_at_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notifications'
          AND column_name = 'deleted_at'
    ) THEN
        ALTER TABLE notifications ADD COLUMN deleted_at DATETIME(3) NULL AFTER read_at;
    END IF;
END $$
DELIMITER ;
CALL add_notification_deleted_at_if_missing();
DROP PROCEDURE add_notification_deleted_at_if_missing;

CREATE TABLE IF NOT EXISTS ticket_ratings (
    rating_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_id CHAR(36) NOT NULL,
    rating TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_ticket_ratings_item FOREIGN KEY (item_id) REFERENCES items(item_id),
    CONSTRAINT fk_ticket_ratings_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT chk_ticket_ratings_value CHECK (rating BETWEEN 1 AND 5),
    UNIQUE INDEX uk_ticket_ratings_item_user (item_id, user_id),
    UNIQUE INDEX uk_ticket_ratings_event (event_id)
);

-- 历史 MongoDB 评分无法在 SQL 中可靠反查，升级后首次新评价会写入唯一登记。
-- 旧工单 SLA 字段保持 NULL，避免把升级前的历史工单误判为超时。
