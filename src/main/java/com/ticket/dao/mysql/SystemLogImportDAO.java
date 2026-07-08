package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.exception.DBException;
import com.ticket.model.SystemLog;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;

public class SystemLogImportDAO extends BaseDAO {
    public SystemLogImportDAO() {
        super();
    }

    SystemLogImportDAO(DataSource dataSource) {
        super(dataSource);
    }

    public int batchInsert(List<SystemLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO system_log_import_records "
            + "(user_id, log_type, log_level, message, ip, operation, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        return executeTransactionCallback(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (SystemLog log : logs) {
                    bindLog(statement, log);
                    statement.addBatch();
                }
                int[] results = statement.executeBatch();
                return affectedRows(results);
            }
        });
    }

    private void bindLog(PreparedStatement statement, SystemLog log) throws Exception {
        if (log == null) {
            throw new DBException("System log cannot be null");
        }
        Long userId = parseUserId(log.getUserId());
        if (userId == null) {
            statement.setNull(1, Types.BIGINT);
        } else {
            statement.setLong(1, userId);
        }
        statement.setString(2, required(log.getLogType(), "log_type"));
        statement.setString(3, required(log.getLogLevel(), "log_level"));
        statement.setString(4, required(log.getMessage(), "message"));

        SystemLog.ActionDetail actionDetail = log.getActionDetail();
        statement.setString(5, actionDetail == null ? null : actionDetail.getIp());
        statement.setString(6, actionDetail == null ? null : actionDetail.getOperation());

        Instant timestamp = log.getTimestamp() == null ? Instant.now() : log.getTimestamp();
        statement.setTimestamp(7, Timestamp.from(timestamp));
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ex) {
            throw new DBException("Invalid user_id for MySQL log import: " + userId, ex);
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DBException("Missing required field for MySQL log import: " + fieldName);
        }
        return value.trim();
    }

    private int affectedRows(int[] results) {
        int affectedRows = 0;
        for (int result : results) {
            if (result == Statement.SUCCESS_NO_INFO) {
                affectedRows++;
            } else if (result != Statement.EXECUTE_FAILED) {
                affectedRows += result;
            }
        }
        return affectedRows;
    }
}
