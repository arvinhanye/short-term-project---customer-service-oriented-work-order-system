USE ticket_management;

DROP PROCEDURE IF EXISTS sp_monthly_report;
DELIMITER $$
CREATE PROCEDURE sp_monthly_report(IN p_year INT, IN p_month INT)
BEGIN
    SELECT
        COUNT(*) AS total_count,
        SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS pending_count,
        SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS processing_count,
        SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS completed_count,
        SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS closed_count,
        SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END) AS cancelled_count,
        COALESCE(SUM(amount), 0.00) AS total_amount,
        COALESCE(AVG(amount), 0.00) AS avg_amount
    FROM orders
    WHERE YEAR(created_at) = p_year
      AND MONTH(created_at) = p_month;
END $$
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_batch_update_order_status;
DELIMITER $$
CREATE PROCEDURE sp_batch_update_order_status(
    IN p_old_status TINYINT,
    IN p_new_status TINYINT,
    IN p_before_time DATETIME
)
BEGIN
    UPDATE orders
    SET status = p_new_status
    WHERE status = p_old_status
      AND created_at <= p_before_time;

    SELECT ROW_COUNT() AS affected_rows;
END $$
DELIMITER ;
