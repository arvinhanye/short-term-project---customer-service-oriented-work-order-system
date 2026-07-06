USE ticket_management;

DROP TRIGGER IF EXISTS trg_order_status_sync;
DELIMITER $$
CREATE TRIGGER trg_order_status_sync
AFTER UPDATE ON orders
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status THEN
        UPDATE items
        SET updated_at = CURRENT_TIMESTAMP
        WHERE item_id = NEW.item_id;
    END IF;
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
