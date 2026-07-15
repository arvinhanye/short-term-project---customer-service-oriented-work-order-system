package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.model.TicketHistory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import javax.sql.DataSource;

public class TicketHistoryDAO extends BaseDAO {
    public TicketHistoryDAO() {
        super();
    }

    public TicketHistoryDAO(DataSource dataSource) {
        super(dataSource);
    }

    public void insert(Connection connection, TicketHistory history) throws Exception {
        String sql = "INSERT INTO ticket_history (event_id, item_id, order_id, event_seq, event_type, visibility, "
            + "actor_user_id, actor_username, actor_role, target_user_id, from_status, to_status, "
            + "from_admin_id, to_admin_id, reason, source_type, source_id, event_payload, occurred_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, history.getEventId());
            statement.setLong(2, history.getItemId());
            setLong(statement, 3, history.getOrderId());
            statement.setLong(4, history.getEventSeq());
            statement.setString(5, history.getEventType());
            statement.setString(6, history.getVisibility());
            setLong(statement, 7, history.getActorUserId());
            statement.setString(8, history.getActorUsername());
            statement.setString(9, history.getActorRole());
            setLong(statement, 10, history.getTargetUserId());
            setInteger(statement, 11, history.getFromStatus());
            setInteger(statement, 12, history.getToStatus());
            setLong(statement, 13, history.getFromAdminId());
            setLong(statement, 14, history.getToAdminId());
            statement.setString(15, history.getReason());
            statement.setString(16, history.getSourceType());
            statement.setString(17, history.getSourceId());
            statement.setString(18, history.getEventPayload() == null ? "{}" : history.getEventPayload());
            statement.setTimestamp(19, Timestamp.valueOf(history.getOccurredAt()));
            statement.executeUpdate();
        }
    }

    public List<TicketHistory> findByItemId(Long itemId, boolean includeStaff, int limit) {
        String visibilityFilter = includeStaff ? "" : " AND visibility = 'PUBLIC'";
        return query("SELECT * FROM ticket_history WHERE item_id = ?" + visibilityFilter
                + " ORDER BY event_seq ASC, history_id ASC LIMIT ?",
            statement -> {
                statement.setLong(1, itemId);
                statement.setInt(2, Math.max(1, Math.min(limit, 500)));
            }, this::mapHistory);
    }

    private TicketHistory mapHistory(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        TicketHistory history = new TicketHistory();
        history.setHistoryId(resultSet.getLong("history_id"));
        history.setEventId(resultSet.getString("event_id"));
        history.setItemId(resultSet.getLong("item_id"));
        history.setOrderId(nullableLong(resultSet, "order_id"));
        history.setEventSeq(resultSet.getLong("event_seq"));
        history.setEventType(resultSet.getString("event_type"));
        history.setVisibility(resultSet.getString("visibility"));
        history.setActorUserId(nullableLong(resultSet, "actor_user_id"));
        history.setActorUsername(resultSet.getString("actor_username"));
        history.setActorRole(resultSet.getString("actor_role"));
        history.setTargetUserId(nullableLong(resultSet, "target_user_id"));
        history.setFromStatus(nullableInteger(resultSet, "from_status"));
        history.setToStatus(nullableInteger(resultSet, "to_status"));
        history.setFromAdminId(nullableLong(resultSet, "from_admin_id"));
        history.setToAdminId(nullableLong(resultSet, "to_admin_id"));
        history.setReason(resultSet.getString("reason"));
        history.setSourceType(resultSet.getString("source_type"));
        history.setSourceId(resultSet.getString("source_id"));
        history.setEventPayload(resultSet.getString("event_payload"));
        history.setOccurredAt(resultSet.getTimestamp("occurred_at").toLocalDateTime());
        history.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        return history;
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT); else statement.setLong(index, value);
    }

    private void setInteger(PreparedStatement statement, int index, Integer value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.TINYINT); else statement.setInt(index, value);
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
