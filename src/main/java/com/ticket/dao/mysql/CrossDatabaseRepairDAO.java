package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import java.util.List;

/** 保存未完成的跨库补偿，避免 MongoDB 短暂故障造成永久脏数据。 */
public class CrossDatabaseRepairDAO extends BaseDAO {
    public void enqueueDeleteItemDetail(long itemId, String error) {
        update("INSERT INTO cross_db_repair_records (repair_type, item_id, last_error) VALUES ('DELETE_ITEM_DETAIL', ?, ?)",
            statement -> {
                statement.setLong(1, itemId);
                statement.setString(2, truncate(error));
            });
    }

    public List<RepairRecord> findPending(int limit) {
        return query("SELECT * FROM cross_db_repair_records WHERE status = 'PENDING' ORDER BY repair_id LIMIT ?",
            statement -> statement.setInt(1, Math.max(1, Math.min(limit, 500))),
            resultSet -> new RepairRecord(resultSet.getLong("repair_id"), resultSet.getString("repair_type"),
                resultSet.getLong("item_id")));
    }

    public void markDone(long repairId) {
        update("UPDATE cross_db_repair_records SET status = 'DONE', attempt_count = attempt_count + 1, last_error = NULL "
            + "WHERE repair_id = ?", statement -> statement.setLong(1, repairId));
    }

    public void markFailed(long repairId, String error) {
        update("UPDATE cross_db_repair_records SET attempt_count = attempt_count + 1, last_error = ? WHERE repair_id = ?",
            statement -> {
                statement.setString(1, truncate(error));
                statement.setLong(2, repairId);
            });
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record RepairRecord(long repairId, String repairType, long itemId) {
    }
}
