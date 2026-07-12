USE ticket_management;

-- P0 可靠日志与跨库补偿：适用于已按旧版本初始化的数据库，可重复执行。
CREATE TABLE IF NOT EXISTS pending_mongo_writes (
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

CREATE TABLE IF NOT EXISTS cross_db_repair_records (
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
