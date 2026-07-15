USE ticket_management;

DROP TRIGGER IF EXISTS trg_order_status_sync;
DELIMITER $$
CREATE TRIGGER trg_order_status_sync
AFTER UPDATE ON orders
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status THEN
        UPDATE items
        SET status = NEW.status,
            updated_at = CURRENT_TIMESTAMP
        WHERE item_id = NEW.item_id;
    END IF;
END $$
DELIMITER ;

-- 工单历史是追加式账本：应用只能 INSERT，禁止事后覆盖或删除。
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

DROP TRIGGER IF EXISTS trg_item_update_time;
DELIMITER $$
CREATE TRIGGER trg_item_update_time
BEFORE UPDATE ON items
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END $$
DELIMITER ;
