package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import java.time.LocalDateTime;

public class DataLifecycleRunDAO extends BaseDAO {
    public void insert(String type, LocalDateTime cutoff, long count, String artifactPath,
                       String checksum, String status, String message, Long actorId) {
        update("INSERT INTO data_lifecycle_runs (run_type, cutoff_at, affected_count, artifact_path, "
                + "artifact_checksum, result_status, result_message, performed_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            statement -> {
                statement.setString(1, type);
                if (cutoff == null) statement.setNull(2, java.sql.Types.TIMESTAMP);
                else statement.setTimestamp(2, java.sql.Timestamp.valueOf(cutoff));
                statement.setLong(3, count);
                statement.setString(4, artifactPath);
                statement.setString(5, checksum);
                statement.setString(6, status);
                statement.setString(7, message == null ? null : message.substring(0, Math.min(1000, message.length())));
                statement.setLong(8, actorId);
            });
    }
}
