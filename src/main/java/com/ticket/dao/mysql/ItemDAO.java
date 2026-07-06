package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.dto.PageResult;
import com.ticket.exception.DBException;
import com.ticket.model.Item;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

public class ItemDAO extends BaseDAO {
    public long insert(Connection connection, Item item) throws Exception {
        String sql = "INSERT INTO items (title, category_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, item.getTitle());
            statement.setLong(2, item.getCategoryId());
            statement.setInt(3, item.getStatus());
            statement.setTimestamp(4, Timestamp.valueOf(item.getCreatedAt()));
            statement.setTimestamp(5, Timestamp.valueOf(item.getUpdatedAt()));
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DBException("Failed to get generated item_id");
        }
    }

    public Item findById(Long itemId) {
        return queryOne("SELECT * FROM items WHERE item_id = ?", statement -> statement.setLong(1, itemId), this::mapItem);
    }

    public void updateTitle(Long itemId, String title, Long categoryId) {
        update("UPDATE items SET title = ?, category_id = ? WHERE item_id = ?",
            statement -> {
                statement.setString(1, title);
                statement.setLong(2, categoryId);
                statement.setLong(3, itemId);
            });
    }

    public PageResult<Item> pageByTitle(String keyword, int page, int pageSize) {
        String normalized = keyword == null ? "" : keyword.trim();
        long total = queryOne("SELECT COUNT(*) AS cnt FROM items WHERE title LIKE ?",
            statement -> statement.setString(1, "%" + normalized + "%"), rs -> rs.getLong("cnt"));
        List<Item> items = query(
            "SELECT * FROM items WHERE title LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            statement -> {
                statement.setString(1, "%" + normalized + "%");
                statement.setInt(2, pageSize);
                statement.setInt(3, Math.max(0, (page - 1) * pageSize));
            },
            this::mapItem
        );
        return new PageResult<>(items, total, page, pageSize);
    }

    private Item mapItem(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Item item = new Item();
        item.setItemId(resultSet.getLong("item_id"));
        item.setTitle(resultSet.getString("title"));
        item.setCategoryId(resultSet.getLong("category_id"));
        item.setStatus(resultSet.getInt("status"));
        item.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        item.setUpdatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime());
        return item;
    }
}
