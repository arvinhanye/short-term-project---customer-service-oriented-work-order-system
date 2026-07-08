package com.ticket.dao.mysql;

import com.ticket.model.SystemLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemLogImportDAOTest {
    private JdbcDataSource dataSource;
    private SystemLogImportDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:system_log_import_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection()) {
            executeSql(connection, "DROP TABLE IF EXISTS system_log_import_records");
            executeSql(connection, "DROP TABLE IF EXISTS users");
            executeSql(connection, """
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY,
                    username VARCHAR(50) NOT NULL
                )
                """);
            executeSql(connection, "INSERT INTO users (user_id, username) VALUES (10001, 'admin01')");
            executeSql(connection, """
                CREATE TABLE system_log_import_records (
                    import_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NULL,
                    log_type VARCHAR(50) NOT NULL,
                    log_level VARCHAR(20) NOT NULL,
                    message VARCHAR(500) NOT NULL,
                    ip VARCHAR(64),
                    operation VARCHAR(200),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_log_import_user
                        FOREIGN KEY (user_id) REFERENCES users(user_id)
                )
                """);
        }

        dao = new SystemLogImportDAO(dataSource);
    }

    @Test
    void shouldBatchInsertSystemLogsWithJdbcBatch() throws Exception {
        int affectedRows = dao.batchInsert(List.of(
            log("10001", "LOGIN", "INFO", "登录成功", "USER_LOGIN"),
            log(null, "LOGIN_FAIL", "WARN", "登录失败", "USER_LOGIN"),
            log("10001", "DB_ERROR", "ERROR", "数据库异常", "DB_CHECK")
        ));

        Assertions.assertEquals(3, affectedRows);
        Assertions.assertEquals(3, countRows());
    }

    @Test
    void shouldReturnZeroWhenNoLogsNeedImport() {
        Assertions.assertEquals(0, dao.batchInsert(List.of()));
    }

    private SystemLog log(String userId, String type, String level, String message, String operation) {
        SystemLog log = new SystemLog();
        log.setUserId(userId);
        log.setLogType(type);
        log.setLogLevel(level);
        log.setMessage(message);
        SystemLog.ActionDetail detail = new SystemLog.ActionDetail();
        detail.setIp("127.0.0.1");
        detail.setOperation(operation);
        log.setActionDetail(detail);
        log.setTimestamp(Instant.parse("2026-07-08T01:00:00Z"));
        return log;
    }

    private int countRows() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM system_log_import_records");
             var resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void executeSql(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
