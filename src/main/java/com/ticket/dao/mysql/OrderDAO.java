package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.dto.PageResult;
import com.ticket.dto.CursorPageResult;
import com.ticket.dto.CrossTicketDTO;
import com.ticket.exception.DBException;
import com.ticket.model.Category;
import com.ticket.model.Item;
import com.ticket.model.Order;
import com.ticket.model.User;
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

    /** 一次 MySQL 联表查询满足工单列表所需的摘要字段，避免逐行 DAO 查询。 */
    public PageResult<CrossTicketDTO> pageTicketSummaries(Long userId, Integer status, String keyword, int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizeLimit(pageSize);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        boolean byUser = userId != null;
        boolean byStatus = status != null;
        boolean byKeyword = !normalizedKeyword.isBlank();
        String where = " WHERE 1 = 1" + (byUser ? " AND o.user_id = ?" : "")
            + (byStatus ? " AND o.status = ?" : "") + (byKeyword ? " AND i.title LIKE ?" : "");
        String from = " FROM orders o JOIN items i ON o.item_id = i.item_id JOIN categories c ON i.category_id = c.category_id "
            + "JOIN users u ON o.user_id = u.user_id";
        long total = queryOne("SELECT COUNT(*) AS cnt" + from + where,
            statement -> bindSummaryFilter(statement, userId, status, normalizedKeyword, byUser, byStatus, byKeyword),
            rs -> rs.getLong("cnt"));
        List<CrossTicketDTO> records = query("SELECT o.order_id, o.user_id, o.item_id, o.amount, o.status AS order_status, "
                + "o.created_at AS order_created_at, i.title, i.category_id, i.status AS item_status, "
                + "i.created_at AS item_created_at, i.updated_at AS item_updated_at, c.name AS category_name, u.username"
                + from + where + " ORDER BY o.created_at DESC, o.order_id DESC LIMIT ? OFFSET ?",
            statement -> {
                int index = bindSummaryFilter(statement, userId, status, normalizedKeyword, byUser, byStatus, byKeyword);
                statement.setInt(index++, normalizedPageSize);
                statement.setInt(index, offset(normalizedPage, normalizedPageSize));
            }, this::mapTicketSummary);
        return new PageResult<>(records, total, normalizedPage, normalizedPageSize);
    }

    /**
     * 返回满足 MySQL 条件的全部摘要，供依赖 MongoDB 元数据的跨库筛选先过滤、再分页。
     */
    public List<CrossTicketDTO> listTicketSummaries(Long userId, Integer status, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        boolean byUser = userId != null;
        boolean byStatus = status != null;
        boolean byKeyword = !normalizedKeyword.isBlank();
        String where = " WHERE 1 = 1" + (byUser ? " AND o.user_id = ?" : "")
            + (byStatus ? " AND o.status = ?" : "") + (byKeyword ? " AND i.title LIKE ?" : "");
        String from = " FROM orders o JOIN items i ON o.item_id = i.item_id JOIN categories c ON i.category_id = c.category_id "
            + "JOIN users u ON o.user_id = u.user_id";
        return query("SELECT o.order_id, o.user_id, o.item_id, o.amount, o.status AS order_status, "
                + "o.created_at AS order_created_at, i.title, i.category_id, i.status AS item_status, "
                + "i.created_at AS item_created_at, i.updated_at AS item_updated_at, c.name AS category_name, u.username"
                + from + where + " ORDER BY o.created_at DESC, o.order_id DESC",
            statement -> bindSummaryFilter(statement, userId, status, normalizedKeyword, byUser, byStatus, byKeyword),
            this::mapTicketSummary);
    }

    public CursorPageResult<CrossTicketDTO> pageTicketSummariesAfter(Long userId, Integer status, String keyword,
                                                                       java.time.LocalDateTime cursorCreatedAt,
                                                                       Long cursorOrderId, int pageSize) {
        int limit = normalizeLimit(pageSize);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        boolean byUser = userId != null;
        boolean byStatus = status != null;
        boolean byKeyword = !normalizedKeyword.isBlank();
        boolean hasCursor = cursorCreatedAt != null && cursorOrderId != null;
        String countWhere = " WHERE 1 = 1" + (byUser ? " AND o.user_id = ?" : "")
            + (byStatus ? " AND o.status = ?" : "") + (byKeyword ? " AND i.title LIKE ?" : "");
        String where = countWhere
            + (hasCursor ? " AND (o.created_at < ? OR (o.created_at = ? AND o.order_id < ?))" : "");
        String from = " FROM orders o JOIN items i ON o.item_id = i.item_id JOIN categories c ON i.category_id = c.category_id "
            + "JOIN users u ON o.user_id = u.user_id";
        long total = queryOne("SELECT COUNT(*) AS cnt" + from + countWhere,
            statement -> bindSummaryFilter(statement, userId, status, normalizedKeyword, byUser, byStatus, byKeyword),
            rs -> rs.getLong("cnt"));
        List<CrossTicketDTO> records = query("SELECT o.order_id, o.user_id, o.item_id, o.amount, o.status AS order_status, "
                + "o.created_at AS order_created_at, i.title, i.category_id, i.status AS item_status, "
                + "i.created_at AS item_created_at, i.updated_at AS item_updated_at, c.name AS category_name, u.username"
                + from + where + " ORDER BY o.created_at DESC, o.order_id DESC LIMIT ?",
            statement -> {
                int index = bindSummaryFilter(statement, userId, status, normalizedKeyword, byUser, byStatus, byKeyword);
                if (hasCursor) {
                    statement.setTimestamp(index++, Timestamp.valueOf(cursorCreatedAt));
                    statement.setTimestamp(index++, Timestamp.valueOf(cursorCreatedAt));
                    statement.setLong(index++, cursorOrderId);
                }
                statement.setInt(index, limit + 1);
            }, this::mapTicketSummary);
        boolean hasNext = records.size() > limit;
        if (hasNext) {
            records.remove(records.size() - 1);
        }
        CrossTicketDTO last = records.isEmpty() ? null : records.get(records.size() - 1);
        return new CursorPageResult<>(records, total,
            hasNext && last != null ? last.getOrder().getCreatedAt() : null,
            hasNext && last != null ? last.getOrder().getOrderId() : null);
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

    private int bindSummaryFilter(PreparedStatement statement, Long userId, Integer status, String keyword,
                                  boolean byUser, boolean byStatus, boolean byKeyword) throws java.sql.SQLException {
        int index = 1;
        if (byUser) {
            statement.setLong(index++, userId);
        }
        if (byStatus) {
            statement.setInt(index++, status);
        }
        if (byKeyword) {
            statement.setString(index++, "%" + keyword + "%");
        }
        return index;
    }

    private CrossTicketDTO mapTicketSummary(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Item item = new Item();
        item.setItemId(resultSet.getLong("item_id"));
        item.setTitle(resultSet.getString("title"));
        item.setCategoryId(resultSet.getLong("category_id"));
        item.setStatus(resultSet.getInt("item_status"));
        item.setCreatedAt(resultSet.getTimestamp("item_created_at").toLocalDateTime());
        item.setUpdatedAt(resultSet.getTimestamp("item_updated_at").toLocalDateTime());
        Order order = new Order();
        order.setOrderId(resultSet.getLong("order_id"));
        order.setUserId(resultSet.getLong("user_id"));
        order.setItemId(item.getItemId());
        order.setAmount(resultSet.getBigDecimal("amount"));
        order.setStatus(resultSet.getInt("order_status"));
        order.setCreatedAt(resultSet.getTimestamp("order_created_at").toLocalDateTime());
        Category category = new Category();
        category.setCategoryId(item.getCategoryId());
        category.setName(resultSet.getString("category_name"));
        User user = new User();
        user.setUserId(order.getUserId());
        user.setUsername(resultSet.getString("username"));
        CrossTicketDTO dto = new CrossTicketDTO();
        dto.setItem(item);
        dto.setOrder(order);
        dto.setCategory(category);
        dto.setUser(user);
        return dto;
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
