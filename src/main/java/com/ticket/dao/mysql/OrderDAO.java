package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.dto.PageResult;
import com.ticket.exception.DBException;
import com.ticket.model.Order;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import javax.sql.DataSource;

public class OrderDAO extends BaseDAO {
    public OrderDAO() {
        super();
    }

    public OrderDAO(DataSource dataSource) {
        super(dataSource);
    }

    public long insert(Connection connection, Order order) throws Exception {
        String sql = "INSERT INTO orders (user_id, item_id, amount, status, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, order.getUserId());
            statement.setLong(2, order.getItemId());
            statement.setBigDecimal(3, order.getAmount());
            statement.setInt(4, order.getStatus());
            statement.setTimestamp(5, Timestamp.valueOf(order.getCreatedAt()));
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DBException("Failed to get generated order_id");
        }
    }

    public Order findByItemId(Long itemId) {
        return queryOne("SELECT * FROM orders WHERE item_id = ?", statement -> statement.setLong(1, itemId), this::mapOrder);
    }

    public List<Order> findRecentByUser(Long userId, int limit) {
        return query("SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
            statement -> {
                statement.setLong(1, userId);
                statement.setInt(2, normalizeLimit(limit));
            }, this::mapOrder);
    }

    public List<Long> findRecentCategoryIdsByUser(Long userId, int limit) {
        return query("SELECT i.category_id FROM orders o JOIN items i ON o.item_id = i.item_id "
                + "WHERE o.user_id = ? ORDER BY o.created_at DESC, o.order_id DESC LIMIT ?",
            statement -> {
                statement.setLong(1, userId);
                statement.setInt(2, normalizeLimit(limit));
            }, resultSet -> resultSet.getLong("category_id"));
    }

    public List<Order> findRecent(int limit) {
        return query("SELECT * FROM orders ORDER BY created_at DESC LIMIT ?",
            statement -> statement.setInt(1, normalizeLimit(limit)), this::mapOrder);
    }

    public PageResult<Order> pageByUserAndStatus(Long userId, Integer status, int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizeLimit(pageSize);
        long total = status == null
            ? queryOne("SELECT COUNT(*) AS cnt FROM orders WHERE user_id = ?",
                statement -> statement.setLong(1, userId), rs -> rs.getLong("cnt"))
            : queryOne("SELECT COUNT(*) AS cnt FROM orders WHERE user_id = ? AND status = ?",
                statement -> {
                    statement.setLong(1, userId);
                    statement.setInt(2, status);
                }, rs -> rs.getLong("cnt"));

        List<Order> orders = status == null
            ? query("SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                statement -> {
                    statement.setLong(1, userId);
                    statement.setInt(2, normalizedPageSize);
                    statement.setInt(3, offset(normalizedPage, normalizedPageSize));
                }, this::mapOrder)
            : query("SELECT * FROM orders WHERE user_id = ? AND status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                statement -> {
                    statement.setLong(1, userId);
                    statement.setInt(2, status);
                    statement.setInt(3, normalizedPageSize);
                    statement.setInt(4, offset(normalizedPage, normalizedPageSize));
                }, this::mapOrder);
        return new PageResult<>(orders, total, normalizedPage, normalizedPageSize);
    }

    public PageResult<Order> pageByUserStatusAndKeyword(Long userId, Integer status, String keyword, int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizeLimit(pageSize);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        boolean hasStatus = status != null;
        boolean hasKeyword = !normalizedKeyword.isBlank();
        String whereSql = " WHERE o.user_id = ?"
            + (hasStatus ? " AND o.status = ?" : "")
            + (hasKeyword ? " AND i.title LIKE ?" : "");

        long total = queryOne("SELECT COUNT(*) AS cnt FROM orders o JOIN items i ON o.item_id = i.item_id" + whereSql,
            statement -> bindUserStatusKeyword(statement, userId, status, normalizedKeyword, hasStatus, hasKeyword),
            rs -> rs.getLong("cnt"));

        List<Order> orders = query("SELECT o.* FROM orders o JOIN items i ON o.item_id = i.item_id"
                + whereSql
                + " ORDER BY i.updated_at DESC, o.created_at DESC LIMIT ? OFFSET ?",
            statement -> {
                int index = bindUserStatusKeyword(statement, userId, status, normalizedKeyword, hasStatus, hasKeyword);
                statement.setInt(index++, normalizedPageSize);
                statement.setInt(index, offset(normalizedPage, normalizedPageSize));
            }, this::mapOrder);
        return new PageResult<>(orders, total, normalizedPage, normalizedPageSize);
    }

    public PageResult<Order> pageAllByStatus(Integer status, int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizeLimit(pageSize);
        long total = status == null
            ? queryOne("SELECT COUNT(*) AS cnt FROM orders", null, rs -> rs.getLong("cnt"))
            : queryOne("SELECT COUNT(*) AS cnt FROM orders WHERE status = ?",
                statement -> statement.setInt(1, status), rs -> rs.getLong("cnt"));

        List<Order> orders = status == null
            ? query("SELECT * FROM orders ORDER BY created_at DESC LIMIT ? OFFSET ?",
                statement -> {
                    statement.setInt(1, normalizedPageSize);
                    statement.setInt(2, offset(normalizedPage, normalizedPageSize));
                }, this::mapOrder)
            : query("SELECT * FROM orders WHERE status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                statement -> {
                    statement.setInt(1, status);
                    statement.setInt(2, normalizedPageSize);
                    statement.setInt(3, offset(normalizedPage, normalizedPageSize));
                }, this::mapOrder);
        return new PageResult<>(orders, total, normalizedPage, normalizedPageSize);
    }

    public void updateStatus(Connection connection, Long orderId, int status) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE orders SET status = ? WHERE order_id = ?")) {
            statement.setInt(1, status);
            statement.setLong(2, orderId);
            statement.executeUpdate();
        }
    }

    public Order findById(Long orderId) {
        return queryOne("SELECT * FROM orders WHERE order_id = ?", statement -> statement.setLong(1, orderId), this::mapOrder);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int offset(int page, int pageSize) {
        return Math.max(0, (page - 1) * pageSize);
    }

    private int bindUserStatusKeyword(PreparedStatement statement, Long userId, Integer status, String keyword,
                                      boolean hasStatus, boolean hasKeyword) throws java.sql.SQLException {
        int index = 1;
        statement.setLong(index++, userId);
        if (hasStatus) {
            statement.setInt(index++, status);
        }
        if (hasKeyword) {
            statement.setString(index++, "%" + keyword + "%");
        }
        return index;
    }

    private Order mapOrder(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Order order = new Order();
        order.setOrderId(resultSet.getLong("order_id"));
        order.setUserId(resultSet.getLong("user_id"));
        order.setItemId(resultSet.getLong("item_id"));
        order.setAmount(resultSet.getBigDecimal("amount"));
        order.setStatus(resultSet.getInt("status"));
        order.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        return order;
    }
}
