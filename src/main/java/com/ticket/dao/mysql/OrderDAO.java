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

public class OrderDAO extends BaseDAO {
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

    public PageResult<Order> pageByUserAndStatus(Long userId, Integer status, int page, int pageSize) {
        String countSql = "SELECT COUNT(*) AS cnt FROM orders WHERE user_id = ? AND (? IS NULL OR status = ?)";
        long total = queryOne(countSql, statement -> {
            statement.setLong(1, userId);
            if (status == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, status);
                statement.setInt(3, status);
            }
        }, rs -> rs.getLong("cnt"));

        List<Order> orders = query(
            "SELECT * FROM orders WHERE user_id = ? AND (? IS NULL OR status = ?) ORDER BY created_at DESC LIMIT ? OFFSET ?",
            statement -> {
                statement.setLong(1, userId);
                if (status == null) {
                    statement.setNull(2, java.sql.Types.INTEGER);
                    statement.setNull(3, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(2, status);
                    statement.setInt(3, status);
                }
                statement.setInt(4, pageSize);
                statement.setInt(5, Math.max(0, (page - 1) * pageSize));
            },
            this::mapOrder
        );
        return new PageResult<>(orders, total, page, pageSize);
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
