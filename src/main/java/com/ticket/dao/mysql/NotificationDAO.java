package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.model.Notification;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.ArrayList;
import javax.sql.DataSource;

public class NotificationDAO extends BaseDAO {
    public NotificationDAO() { super(); }
    NotificationDAO(DataSource dataSource) { super(dataSource); }

    public void insert(Connection connection, Long userId, Long itemId, String type,
                       String title, String content, String dedupKey) throws Exception {
        if (userId == null) return;
        String sql = "INSERT INTO notifications (user_id, item_id, notification_type, title, content, dedup_key) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE notification_id = notification_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            if (itemId == null) statement.setNull(2, Types.BIGINT); else statement.setLong(2, itemId);
            statement.setString(3, type);
            statement.setString(4, trim(title, 160));
            statement.setString(5, trim(content, 500));
            statement.setString(6, dedupKey == null ? null : trim(dedupKey, 160));
            statement.executeUpdate();
        }
    }

    public String preference(Connection connection, Long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT u.role, COALESCE(p.notification_preference, 'ALL') AS preference "
                    + "FROM users u LEFT JOIN profiles p ON p.user_id = u.user_id WHERE u.user_id = ?")) {
            statement.setLong(1, userId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return "NONE";
                return "USER".equals(resultSet.getString("role"))
                    ? resultSet.getString("preference") : "ALL";
            }
        }
    }

    public List<Notification> findRecent(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return query("SELECT * FROM notifications WHERE user_id = ? AND deleted_at IS NULL "
            + "ORDER BY created_at DESC LIMIT ?", statement -> {
            statement.setLong(1, userId);
            statement.setInt(2, safeLimit);
        }, this::map);
    }

    public long countUnread(Long userId) {
        Long count = queryOne("SELECT COUNT(*) AS cnt FROM notifications WHERE user_id = ? "
                + "AND read_at IS NULL AND deleted_at IS NULL",
            statement -> statement.setLong(1, userId), resultSet -> resultSet.getLong("cnt"));
        return count == null ? 0L : count;
    }

    public void markAllRead(Long userId) {
        update("UPDATE notifications SET read_at = CURRENT_TIMESTAMP(3) WHERE user_id = ? "
                + "AND read_at IS NULL AND deleted_at IS NULL",
            statement -> statement.setLong(1, userId));
    }

    public int deleteOwned(Long userId, Long notificationId) {
        if (userId == null || notificationId == null) return 0;
        return update("UPDATE notifications SET deleted_at = CURRENT_TIMESTAMP(3) "
                + "WHERE notification_id = ? AND user_id = ? AND deleted_at IS NULL", statement -> {
            statement.setLong(1, notificationId);
            statement.setLong(2, userId);
        });
    }

    public int deleteRead(Long userId) {
        if (userId == null) return 0;
        return update("UPDATE notifications SET deleted_at = CURRENT_TIMESTAMP(3) "
                + "WHERE user_id = ? AND read_at IS NOT NULL AND deleted_at IS NULL",
            statement -> statement.setLong(1, userId));
    }

    public List<Long> findActiveRootIds(Connection connection) throws Exception {
        List<Long> userIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id FROM users WHERE role = 'ROOT' AND status = 1 ORDER BY user_id");
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) userIds.add(resultSet.getLong(1));
        }
        return userIds;
    }

    private Notification map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Notification value = new Notification();
        value.setNotificationId(resultSet.getLong("notification_id"));
        value.setUserId(resultSet.getLong("user_id"));
        long itemId = resultSet.getLong("item_id");
        value.setItemId(resultSet.wasNull() ? null : itemId);
        value.setNotificationType(resultSet.getString("notification_type"));
        value.setTitle(resultSet.getString("title"));
        value.setContent(resultSet.getString("content"));
        Timestamp readAt = resultSet.getTimestamp("read_at");
        value.setReadAt(readAt == null ? null : readAt.toLocalDateTime());
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        value.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        return value;
    }

    private String trim(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
