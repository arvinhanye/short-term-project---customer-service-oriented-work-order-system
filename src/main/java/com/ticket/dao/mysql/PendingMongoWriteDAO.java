package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.model.ActionLog;
import com.ticket.model.SystemLog;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 持久化 MongoDB 写入失败的日志，供启动时重放。 */
public class PendingMongoWriteDAO extends BaseDAO {
    public void enqueueAction(ActionLog log, String error) {
        ActionLog.ClientInfo client = log.getClientInfo() == null ? new ActionLog.ClientInfo() : log.getClientInfo();
        insert(new PendingWrite("ACTION", log.getUserId(), log.getItemId(), log.getActionType(), null, null, null,
            client.getClientType(), client.getIp(), log.getCreatedAt(), error));
    }

    public void enqueueSystem(SystemLog log, String error) {
        SystemLog.ActionDetail detail = log.getActionDetail() == null ? new SystemLog.ActionDetail() : log.getActionDetail();
        insert(new PendingWrite("SYSTEM", log.getUserId(), null, log.getLogType(), log.getLogLevel(), log.getMessage(),
            detail.getOperation(), null, detail.getIp(), log.getTimestamp(), error));
    }

    public List<PendingWrite> findPending(int limit) {
        return query("SELECT * FROM pending_mongo_writes WHERE status = 'PENDING' ORDER BY retry_id LIMIT ?",
            statement -> statement.setInt(1, Math.max(1, Math.min(limit, 500))), this::map);
    }

    public void markDone(long retryId) {
        update("UPDATE pending_mongo_writes SET status = 'DONE', attempt_count = attempt_count + 1, last_error = NULL "
            + "WHERE retry_id = ?", statement -> statement.setLong(1, retryId));
    }

    public void markFailed(long retryId, String error) {
        update("UPDATE pending_mongo_writes SET attempt_count = attempt_count + 1, last_error = ? WHERE retry_id = ?",
            statement -> {
                statement.setString(1, truncate(error));
                statement.setLong(2, retryId);
            });
    }

    private void insert(PendingWrite write) {
        update("INSERT INTO pending_mongo_writes (write_type, user_id, item_id, log_type, log_level, message, operation, "
                + "client_type, ip, occurred_at, last_error) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", statement -> {
            statement.setString(1, write.writeType());
            statement.setString(2, write.userId());
            statement.setString(3, write.itemId());
            statement.setString(4, write.logType());
            statement.setString(5, write.logLevel());
            statement.setString(6, write.message());
            statement.setString(7, write.operation());
            statement.setString(8, write.clientType());
            statement.setString(9, write.ip());
            statement.setTimestamp(10, Timestamp.from(write.occurredAt()));
            statement.setString(11, truncate(write.lastError()));
        });
    }

    private PendingWrite map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PendingWrite(resultSet.getLong("retry_id"), resultSet.getString("write_type"), resultSet.getString("user_id"),
            resultSet.getString("item_id"), resultSet.getString("log_type"), resultSet.getString("log_level"),
            resultSet.getString("message"), resultSet.getString("operation"), resultSet.getString("client_type"),
            resultSet.getString("ip"), resultSet.getTimestamp("occurred_at").toInstant(), resultSet.getString("last_error"));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record PendingWrite(long retryId, String writeType, String userId, String itemId, String logType, String logLevel,
                               String message, String operation, String clientType, String ip, Instant occurredAt,
                               String lastError) {
        PendingWrite(String writeType, String userId, String itemId, String logType, String logLevel, String message,
                     String operation, String clientType, String ip, Instant occurredAt, String lastError) {
            this(0L, writeType, userId, itemId, logType, logLevel, message, operation, clientType, ip,
                occurredAt == null ? Instant.now() : occurredAt, lastError);
        }
    }
}
