USE ticket_management;

-- 无损增量升级：不删除账号、工单、评论或日志。
DROP PROCEDURE IF EXISTS add_ticket_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_ticket_column_if_missing(IN p_name VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = p_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE orders ADD COLUMN ', p_name, ' ', p_definition);
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END $$
DELIMITER ;

CALL add_ticket_column_if_missing('assigned_admin_id', 'BIGINT NULL');
CALL add_ticket_column_if_missing('transfer_request_id', 'CHAR(36) NULL');
CALL add_ticket_column_if_missing('transfer_requested_by', 'BIGINT NULL');
CALL add_ticket_column_if_missing('transfer_target_admin_id', 'BIGINT NULL');
CALL add_ticket_column_if_missing('transfer_reason', 'VARCHAR(200) NULL');
CALL add_ticket_column_if_missing('transfer_requested_at', 'DATETIME(3) NULL');
CALL add_ticket_column_if_missing('reminder_count', 'INT NOT NULL DEFAULT 0');
CALL add_ticket_column_if_missing('last_reminded_at', 'DATETIME(3) NULL');
CALL add_ticket_column_if_missing('workflow_version', 'BIGINT NOT NULL DEFAULT 0');
DROP PROCEDURE add_ticket_column_if_missing;

CREATE TABLE IF NOT EXISTS ticket_history (
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

DROP PROCEDURE IF EXISTS add_ticket_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_ticket_index_if_missing(IN p_name VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = p_name
    ) THEN
        SET @ddl = CONCAT('CREATE INDEX ', p_name, ' ON orders (', p_columns, ')');
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END $$
DELIMITER ;

CALL add_ticket_index_if_missing('idx_orders_assigned_status_created_at', 'assigned_admin_id, status, created_at');
CALL add_ticket_index_if_missing('idx_orders_transfer_target_requested_at', 'transfer_target_admin_id, transfer_requested_at');
DROP PROCEDURE add_ticket_index_if_missing;

-- 在 MongoDB 当前工作流字段回填后，由迁移服务为每张旧工单追加 MIGRATION_SNAPSHOT。

DROP TRIGGER IF EXISTS trg_ticket_history_no_update;
DELIMITER $$
CREATE TRIGGER trg_ticket_history_no_update
BEFORE UPDATE ON ticket_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ticket_history is append-only';
END $$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_ticket_history_no_delete;
DELIMITER $$
CREATE TRIGGER trg_ticket_history_no_delete
BEFORE DELETE ON ticket_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ticket_history is append-only';
END $$
DELIMITER ;
