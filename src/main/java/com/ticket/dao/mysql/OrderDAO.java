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
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        String sql = "INSERT INTO orders (user_id, item_id, amount, status, assigned_admin_id, reminder_count, "
            + "sla_policy_id, first_response_due_at, resolution_due_at, sla_state, workflow_version, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, order.getUserId());
            statement.setLong(2, order.getItemId());
            statement.setBigDecimal(3, order.getAmount());
            statement.setInt(4, order.getStatus());
            setNullableLong(statement, 5, order.getAssignedAdminId());
            statement.setInt(6, order.getReminderCount());
            setNullableLong(statement, 7, order.getSlaPolicyId());
            setNullableTimestamp(statement, 8, order.getFirstResponseDueAt());
            setNullableTimestamp(statement, 9, order.getResolutionDueAt());
            statement.setString(10, order.getSlaState());
            statement.setLong(11, order.getWorkflowVersion());
            statement.setTimestamp(12, Timestamp.valueOf(order.getCreatedAt()));
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

    public List<Long> findUnmigratedItemIds(int limit) {
        List<Long> itemIds = new ArrayList<>();
        try (Connection connection = getWriteConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT item_id FROM orders WHERE workflow_version = 0 ORDER BY item_id LIMIT ?")) {
            statement.setInt(1, Math.max(1, Math.min(limit, 1000)));
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) itemIds.add(resultSet.getLong("item_id"));
            }
            return itemIds;
        } catch (Exception ex) {
            throw new DBException("Failed to query unmigrated workflows from write database", ex);
        }
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
        boolean byPendingConfirmation = Integer.valueOf(99).equals(status);
        boolean byStatus = status != null && !byPendingConfirmation;
        boolean byKeyword = !normalizedKeyword.isBlank();
        String where = " WHERE 1 = 1" + (byUser ? " AND o.user_id = ?" : "")
            + (byStatus ? " AND o.status = ?" : "")
            + (byPendingConfirmation ? " AND o.transfer_request_id IS NOT NULL" : "")
            + (byKeyword ? " AND i.title LIKE ?" : "");
        String from = " FROM orders o JOIN items i ON o.item_id = i.item_id JOIN categories c ON i.category_id = c.category_id "
            + "JOIN users u ON o.user_id = u.user_id";
        long total = queryOne("SELECT COUNT(*) AS cnt" + from + where,
            statement -> bindSummaryFilter(statement, userId, status, normalizedKeyword, byUser, byStatus, byKeyword),
            rs -> rs.getLong("cnt"));
        List<CrossTicketDTO> records = query("SELECT " + summaryColumns()
                + from + where + " ORDER BY " + slaDueOrder() + ", o.created_at DESC, o.order_id DESC LIMIT ? OFFSET ?",
            statement -> {
                int index = bindSummaryFilter(statement, userId, status, normalizedKeyword, byUser, byStatus, byKeyword);
                statement.setInt(index++, normalizedPageSize);
                statement.setInt(index, offset(normalizedPage, normalizedPageSize));
            }, this::mapTicketSummary);
        return new PageResult<>(records, total, normalizedPage, normalizedPageSize);
    }

    /** 负责人筛选直接下推到 MySQL，避免先加载全量 MongoDB 详情再在内存分页。 */
    public PageResult<CrossTicketDTO> pageTicketSummariesByAssignment(Integer status, String keyword,
                                                                       String assignmentScope, Long adminId,
                                                                       int page, int pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizeLimit(pageSize);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        boolean byPendingConfirmation = Integer.valueOf(99).equals(status);
        boolean byStatus = status != null && !byPendingConfirmation;
        boolean byKeyword = !normalizedKeyword.isBlank();
        String assignmentClause = assignmentClause(assignmentScope);
        boolean needsAdmin = !"UNASSIGNED".equals(assignmentScope);
        if (needsAdmin && adminId == null) {
            return new PageResult<>(List.of(), 0, normalizedPage, normalizedPageSize);
        }
        String where = " WHERE 1 = 1" + (byStatus ? " AND o.status = ?" : "")
            + (byPendingConfirmation ? " AND o.transfer_request_id IS NOT NULL" : "")
            + (byKeyword ? " AND i.title LIKE ?" : "") + assignmentClause;
        String from = summaryFrom();
        long total = queryOne("SELECT COUNT(*) AS cnt" + from + where,
            statement -> bindAssignmentFilter(statement, status, normalizedKeyword, byStatus, byKeyword,
                needsAdmin, adminId), rs -> rs.getLong("cnt"));
        List<CrossTicketDTO> records = query("SELECT " + summaryColumns() + from + where
                + " ORDER BY " + slaDueOrder() + ", o.created_at DESC, o.order_id DESC LIMIT ? OFFSET ?",
            statement -> {
                int index = bindAssignmentFilter(statement, status, normalizedKeyword, byStatus, byKeyword,
                    needsAdmin, adminId);
                statement.setInt(index++, normalizedPageSize);
                statement.setInt(index, offset(normalizedPage, normalizedPageSize));
            }, this::mapTicketSummary);
        return new PageResult<>(records, total, normalizedPage, normalizedPageSize);
    }

    public List<CrossTicketDTO> listTicketSummariesByAssignment(String assignmentScope, Long adminId) {
        String assignmentClause = assignmentClause(assignmentScope);
        boolean needsAdmin = !"UNASSIGNED".equals(assignmentScope);
        if (needsAdmin && adminId == null) return List.of();
        return query("SELECT " + summaryColumns() + summaryFrom() + " WHERE 1 = 1" + assignmentClause
                + " ORDER BY " + slaDueOrder() + ", o.created_at DESC, o.order_id DESC",
            statement -> {
                if (needsAdmin) statement.setLong(1, adminId);
            }, this::mapTicketSummary);
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
        return query("SELECT " + summaryColumns()
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
        List<CrossTicketDTO> records = query("SELECT " + summaryColumns()
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

    public Order findByItemIdForUpdate(Connection connection, Long itemId) throws Exception {
        return findOne(connection, "SELECT * FROM orders WHERE item_id = ? FOR UPDATE", itemId);
    }

    public Order findByIdForUpdate(Connection connection, Long orderId) throws Exception {
        return findOne(connection, "SELECT * FROM orders WHERE order_id = ? FOR UPDATE", orderId);
    }

    /** 工单状态、负责人和工作流版本一起作为乐观锁。 */
    public int updateStatusIfCurrent(Connection connection, Order current, Long actorAdminId, int newStatus)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET status = ?, resolved_at = CASE WHEN ? IN (2, 3) "
                    + "THEN COALESCE(resolved_at, CURRENT_TIMESTAMP(3)) ELSE resolved_at END, "
                    + "reopen_deadline_at = CASE WHEN ? = 2 THEN ? "
                    + "ELSE reopen_deadline_at END, "
                    + "sla_state = CASE WHEN ? = 4 THEN 'CANCELLED' WHEN ? = 2 THEN "
                    + "CASE WHEN sla_state = 'BREACHED' "
                    + "OR (first_response_due_at IS NOT NULL AND ((first_responded_at IS NULL "
                    + "AND CURRENT_TIMESTAMP(3) > first_response_due_at) "
                    + "OR first_responded_at > first_response_due_at)) "
                    + "OR (next_response_due_at IS NOT NULL AND CURRENT_TIMESTAMP(3) > next_response_due_at) "
                    + "OR (resolution_due_at IS NOT NULL AND CURRENT_TIMESTAMP(3) > resolution_due_at) "
                    + "THEN 'BREACHED' ELSE 'MET' END ELSE sla_state END, "
                    + "workflow_version = workflow_version + 1 "
                    + "WHERE order_id = ? AND status = ? AND workflow_version = ? "
                    + "AND assigned_admin_id = ? AND transfer_request_id IS NULL")) {
            statement.setInt(1, newStatus);
            statement.setInt(2, newStatus);
            statement.setInt(3, newStatus);
            setNullableTimestamp(statement, 4, newStatus == 2 ? LocalDateTime.now().plusDays(7) : null);
            statement.setInt(5, newStatus);
            statement.setInt(6, newStatus);
            statement.setLong(7, current.getOrderId());
            statement.setInt(8, current.getStatus());
            statement.setLong(9, current.getWorkflowVersion());
            statement.setLong(10, actorAdminId);
            return statement.executeUpdate();
        }
    }

    public int pauseSla(Connection connection, Order current, Long actorAdminId, int newStatus, String reason,
                        LocalDateTime pausedAt) throws Exception {
        String sql = "UPDATE orders SET status = ?, sla_paused_at = COALESCE(sla_paused_at, ?), "
            + "sla_pause_reason = ?, workflow_version = workflow_version + 1 "
            + "WHERE order_id = ? AND status = ? AND workflow_version = ? AND assigned_admin_id = ? "
            + "AND transfer_request_id IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, newStatus);
            statement.setTimestamp(2, Timestamp.valueOf(pausedAt));
            statement.setString(3, reason);
            statement.setLong(4, current.getOrderId());
            statement.setInt(5, current.getStatus());
            statement.setLong(6, current.getWorkflowVersion());
            statement.setLong(7, actorAdminId);
            return statement.executeUpdate();
        }
    }

    public int resumeSla(Connection connection, Order current, Long actorAdminId, LocalDateTime resumedAt)
            throws Exception {
        long pausedMinutes = pausedMinutes(current, resumedAt);
        String sql = "UPDATE orders SET status = 1, first_response_due_at = DATE_ADD(first_response_due_at, INTERVAL ? MINUTE), "
            + "next_response_due_at = DATE_ADD(next_response_due_at, INTERVAL ? MINUTE), "
            + "resolution_due_at = DATE_ADD(resolution_due_at, INTERVAL ? MINUTE), "
            + "total_sla_paused_minutes = total_sla_paused_minutes + ?, sla_paused_at = NULL, "
            + "sla_pause_reason = NULL, workflow_version = workflow_version + 1 "
            + "WHERE order_id = ? AND status IN (5, 6) AND workflow_version = ? AND assigned_admin_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, pausedMinutes);
            statement.setLong(2, pausedMinutes);
            statement.setLong(3, pausedMinutes);
            statement.setLong(4, pausedMinutes);
            statement.setLong(5, current.getOrderId());
            statement.setLong(6, current.getWorkflowVersion());
            statement.setLong(7, actorAdminId);
            return statement.executeUpdate();
        }
    }

    public int confirmClose(Connection connection, Order current, Long userId) throws Exception {
        String sql = "UPDATE orders SET status = 3, workflow_version = workflow_version + 1 "
            + "WHERE order_id = ? AND user_id = ? AND status = 2 AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, current.getOrderId());
            statement.setLong(2, userId);
            statement.setLong(3, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int reopen(Connection connection, Order current, Long userId, LocalDateTime nextResponseDueAt,
                      LocalDateTime resolutionDueAt, LocalDateTime now) throws Exception {
        String sql = "UPDATE orders SET status = 1, resolved_at = NULL, reopen_deadline_at = NULL, "
            + "reopen_count = reopen_count + 1, next_response_due_at = ?, resolution_due_at = ?, "
            + "sla_state = CASE WHEN sla_policy_id IS NULL THEN sla_state ELSE 'ACTIVE' END, "
            + "workflow_version = workflow_version + 1 WHERE order_id = ? AND user_id = ? AND status = 2 "
            + "AND reopen_deadline_at IS NOT NULL AND reopen_deadline_at >= ? AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableTimestamp(statement, 1, nextResponseDueAt);
            setNullableTimestamp(statement, 2, resolutionDueAt);
            statement.setLong(3, current.getOrderId());
            statement.setLong(4, userId);
            statement.setTimestamp(5, Timestamp.valueOf(now));
            statement.setLong(6, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int claimIfUnassigned(Connection connection, Order current, Long adminId) throws Exception {
        String sql = "UPDATE orders SET assigned_admin_id = ?, workflow_version = workflow_version + 1 "
            + "WHERE item_id = ? AND status IN (0, 1) AND assigned_admin_id IS NULL "
            + "AND transfer_request_id IS NULL AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, adminId);
            statement.setLong(2, current.getItemId());
            statement.setLong(3, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int requestTransfer(Connection connection, Order current, Long requesterId, Long targetId,
                               String requestId, String reason, LocalDateTime requestedAt) throws Exception {
        String assignmentCondition = current.getAssignedAdminId() == null
            ? "assigned_admin_id IS NULL" : "assigned_admin_id = ?";
        String sql = "UPDATE orders SET transfer_request_id = ?, transfer_requested_by = ?, "
            + "transfer_target_admin_id = ?, transfer_reason = ?, transfer_requested_at = ?, "
            + "workflow_version = workflow_version + 1 WHERE item_id = ? AND status IN (0, 1) AND "
            + assignmentCondition + " AND transfer_request_id IS NULL AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, requestId);
            statement.setLong(index++, requesterId);
            statement.setLong(index++, targetId);
            statement.setString(index++, reason);
            statement.setTimestamp(index++, Timestamp.valueOf(requestedAt));
            statement.setLong(index++, current.getItemId());
            if (current.getAssignedAdminId() != null) {
                statement.setLong(index++, current.getAssignedAdminId());
            }
            statement.setLong(index, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int respondToTransfer(Connection connection, Order current, Long targetId, String requestId,
                                 boolean accept) throws Exception {
        String assignmentUpdate = accept ? "assigned_admin_id = ?, " : "";
        String sql = "UPDATE orders SET " + assignmentUpdate
            + "transfer_request_id = NULL, transfer_requested_by = NULL, transfer_target_admin_id = NULL, "
            + "transfer_reason = NULL, transfer_requested_at = NULL, workflow_version = workflow_version + 1 "
            + "WHERE item_id = ? AND transfer_request_id = ? AND transfer_target_admin_id = ? "
            + "AND workflow_version = ?" + (accept ? " AND status IN (0, 1)" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (accept) statement.setLong(index++, targetId);
            statement.setLong(index++, current.getItemId());
            statement.setString(index++, requestId);
            statement.setLong(index++, targetId);
            statement.setLong(index, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int cancelTransfer(Connection connection, Order current, Long requesterId, String requestId)
            throws Exception {
        String sql = "UPDATE orders SET transfer_request_id = NULL, transfer_requested_by = NULL, "
            + "transfer_target_admin_id = NULL, transfer_reason = NULL, transfer_requested_at = NULL, "
            + "workflow_version = workflow_version + 1 WHERE item_id = ? AND transfer_request_id = ? "
            + "AND transfer_requested_by = ? AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, current.getItemId());
            statement.setString(2, requestId);
            statement.setLong(3, requesterId);
            statement.setLong(4, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int recordReminder(Connection connection, Order current, Long userId,
                              LocalDateTime cooldownCutoff, LocalDateTime remindedAt) throws Exception {
        String sql = "UPDATE orders SET reminder_count = reminder_count + 1, last_reminded_at = ?, "
            + "workflow_version = workflow_version + 1 WHERE item_id = ? AND user_id = ? "
            + "AND status IN (0, 1) AND workflow_version = ? "
            + "AND (last_reminded_at IS NULL OR last_reminded_at <= ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(remindedAt));
            statement.setLong(2, current.getItemId());
            statement.setLong(3, userId);
            statement.setLong(4, current.getWorkflowVersion());
            statement.setTimestamp(5, Timestamp.valueOf(cooldownCutoff));
            return statement.executeUpdate();
        }
    }

    public int incrementWorkflowVersion(Connection connection, Order current) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET workflow_version = workflow_version + 1 WHERE item_id = ? AND workflow_version = ?")) {
            statement.setLong(1, current.getItemId());
            statement.setLong(2, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int recordAdminResponse(Connection connection, Order current, LocalDateTime respondedAt) throws Exception {
        String sql = "UPDATE orders SET first_responded_at = COALESCE(first_responded_at, ?), "
            + "last_admin_response_at = ?, next_response_due_at = NULL, "
            + "sla_state = CASE WHEN sla_state = 'ACTIVE' AND ((first_responded_at IS NULL "
            + "AND first_response_due_at IS NOT NULL AND ? > first_response_due_at) "
            + "OR (next_response_due_at IS NOT NULL AND ? > next_response_due_at) "
            + "OR (resolution_due_at IS NOT NULL AND ? > resolution_due_at)) THEN 'BREACHED' ELSE sla_state END, "
            + "workflow_version = workflow_version + 1 WHERE item_id = ? AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp timestamp = Timestamp.valueOf(respondedAt);
            statement.setTimestamp(1, timestamp);
            statement.setTimestamp(2, timestamp);
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            statement.setTimestamp(5, timestamp);
            statement.setLong(6, current.getItemId());
            statement.setLong(7, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    public int recordCustomerReply(Connection connection, Order current, LocalDateTime nextResponseDueAt)
            throws Exception {
        long pausedMinutes = Integer.valueOf(5).equals(current.getStatus())
            ? pausedMinutes(current, LocalDateTime.now()) : 0;
        String sql = "UPDATE orders SET next_response_due_at = CASE WHEN first_responded_at IS NOT NULL "
            + "THEN ? ELSE DATE_ADD(next_response_due_at, INTERVAL ? MINUTE) END, "
            + "first_response_due_at = DATE_ADD(first_response_due_at, INTERVAL ? MINUTE), "
            + "resolution_due_at = DATE_ADD(resolution_due_at, INTERVAL ? MINUTE), "
            + "status = CASE WHEN status = 5 THEN 1 ELSE status END, "
            + "total_sla_paused_minutes = total_sla_paused_minutes + CASE WHEN status = 5 THEN ? ELSE 0 END, "
            + "sla_paused_at = CASE WHEN status = 5 THEN NULL ELSE sla_paused_at END, "
            + "sla_pause_reason = CASE WHEN status = 5 THEN NULL ELSE sla_pause_reason END, "
            + "workflow_version = workflow_version + 1 "
            + "WHERE item_id = ? AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableTimestamp(statement, 1, nextResponseDueAt);
            statement.setLong(2, pausedMinutes);
            statement.setLong(3, pausedMinutes);
            statement.setLong(4, pausedMinutes);
            statement.setLong(5, pausedMinutes);
            statement.setLong(6, current.getItemId());
            statement.setLong(7, current.getWorkflowVersion());
            return statement.executeUpdate();
        }
    }

    private long pausedMinutes(Order current, LocalDateTime resumedAt) {
        if (current == null || current.getSlaPausedAt() == null || resumedAt == null) return 0;
        return Math.max(0, java.time.Duration.between(current.getSlaPausedAt(), resumedAt).toMinutes());
    }

    public List<Order> findSlaAttentionForAdmin(Long adminId, LocalDateTime threshold) {
        return query("SELECT * FROM orders WHERE assigned_admin_id = ? AND status IN (0, 1) "
                + "AND sla_state IN ('ACTIVE', 'BREACHED') AND (first_response_due_at <= ? "
                + "OR next_response_due_at <= ? OR resolution_due_at <= ?) ORDER BY resolution_due_at, order_id",
            statement -> {
                statement.setLong(1, adminId);
                Timestamp value = Timestamp.valueOf(threshold);
                statement.setTimestamp(2, value);
                statement.setTimestamp(3, value);
                statement.setTimestamp(4, value);
            }, this::mapOrder);
    }

    public List<Order> findSlaAttention(LocalDateTime threshold) {
        return query("SELECT * FROM orders WHERE status IN (0, 1) "
                + "AND sla_state IN ('ACTIVE', 'BREACHED') AND (first_response_due_at <= ? "
                + "OR next_response_due_at <= ? OR resolution_due_at <= ?) "
                + "ORDER BY resolution_due_at, order_id",
            statement -> {
                Timestamp value = Timestamp.valueOf(threshold);
                statement.setTimestamp(1, value);
                statement.setTimestamp(2, value);
                statement.setTimestamp(3, value);
            }, this::mapOrder);
    }

    public void markSlaBreached(Connection connection, Long orderId, LocalDateTime now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET sla_state = 'BREACHED' WHERE order_id = ? AND sla_state = 'ACTIVE' "
                    + "AND ((first_responded_at IS NULL AND first_response_due_at IS NOT NULL AND first_response_due_at < ?) "
                    + "OR (next_response_due_at IS NOT NULL AND next_response_due_at < ?) "
                    + "OR (resolution_due_at IS NOT NULL AND resolution_due_at < ?))")) {
            statement.setLong(1, orderId);
            Timestamp value = Timestamp.valueOf(now);
            statement.setTimestamp(2, value);
            statement.setTimestamp(3, value);
            statement.setTimestamp(4, value);
            statement.executeUpdate();
        }
    }

    public int migrateWorkflowSnapshot(Connection connection, Order current, Long assignedAdminId,
                                       String transferRequestId, Long transferRequestedBy,
                                       Long transferTargetAdminId, String transferReason,
                                       LocalDateTime transferRequestedAt, int reminderCount,
                                       LocalDateTime lastRemindedAt) throws Exception {
        String sql = "UPDATE orders SET assigned_admin_id = ?, transfer_request_id = ?, "
            + "transfer_requested_by = ?, transfer_target_admin_id = ?, transfer_reason = ?, "
            + "transfer_requested_at = ?, reminder_count = ?, last_reminded_at = ?, workflow_version = 1 "
            + "WHERE item_id = ? AND workflow_version = 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableLong(statement, 1, assignedAdminId);
            statement.setString(2, transferRequestId);
            setNullableLong(statement, 3, transferRequestedBy);
            setNullableLong(statement, 4, transferTargetAdminId);
            statement.setString(5, transferReason);
            setNullableTimestamp(statement, 6, transferRequestedAt);
            statement.setInt(7, Math.max(0, reminderCount));
            setNullableTimestamp(statement, 8, lastRemindedAt);
            statement.setLong(9, current.getItemId());
            return statement.executeUpdate();
        }
    }

    public List<Order> findForBatchUpdate(Connection connection, int status, LocalDateTime beforeTime,
                                          Long assignedAdminId)
            throws Exception {
        List<Order> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM orders WHERE status = ? AND created_at <= ? AND assigned_admin_id = ? "
                    + "AND transfer_request_id IS NULL ORDER BY order_id FOR UPDATE")) {
            statement.setInt(1, status);
            statement.setTimestamp(2, Timestamp.valueOf(beforeTime));
            statement.setLong(3, assignedAdminId);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) orders.add(mapOrder(resultSet));
            }
        }
        return orders;
    }

    /** 系统批处理专用：逐张更新并清除已失效的待确认转派，调用方必须同时追加历史。 */
    public int updateStatusForMaintenance(Connection connection, Order current, int newStatus) throws Exception {
        String sql = "UPDATE orders SET status = ?, transfer_request_id = NULL, transfer_requested_by = NULL, "
            + "transfer_target_admin_id = NULL, transfer_reason = NULL, transfer_requested_at = NULL, "
            + "sla_state = CASE WHEN ? = 4 THEN 'CANCELLED' ELSE sla_state END, "
            + "workflow_version = workflow_version + 1 WHERE order_id = ? AND status = ? AND workflow_version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, newStatus);
            statement.setInt(2, newStatus);
            statement.setLong(3, current.getOrderId());
            statement.setInt(4, current.getStatus());
            statement.setLong(5, current.getWorkflowVersion());
            return statement.executeUpdate();
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

    private int bindAssignmentFilter(PreparedStatement statement, Integer status, String keyword,
                                     boolean byStatus, boolean byKeyword, boolean needsAdmin, Long adminId)
            throws java.sql.SQLException {
        int index = 1;
        if (byStatus) statement.setInt(index++, status);
        if (byKeyword) statement.setString(index++, "%" + keyword + "%");
        if (needsAdmin) statement.setLong(index++, adminId);
        return index;
    }

    private String assignmentClause(String assignmentScope) {
        return switch (assignmentScope == null ? "" : assignmentScope) {
            case "UNASSIGNED" -> " AND o.assigned_admin_id IS NULL";
            case "ASSIGNED_TO" -> " AND o.assigned_admin_id = ?";
            case "PENDING_TRANSFER_TO" -> " AND o.transfer_target_admin_id = ?";
            default -> throw new IllegalArgumentException("Unsupported assignment scope: " + assignmentScope);
        };
    }

    private String summaryFrom() {
        return " FROM orders o JOIN items i ON o.item_id = i.item_id JOIN categories c ON i.category_id = c.category_id "
            + "JOIN users u ON o.user_id = u.user_id";
    }

    private String slaDueOrder() {
        return "CASE WHEN o.sla_state = 'BREACHED' THEN 0 WHEN o.sla_state = 'ACTIVE' THEN 1 ELSE 2 END, "
            + "CASE WHEN o.sla_state IN ('ACTIVE', 'BREACHED') THEN CASE "
            + "WHEN o.first_responded_at IS NULL THEN CASE "
            + "WHEN o.first_response_due_at IS NULL THEN o.resolution_due_at "
            + "WHEN o.resolution_due_at IS NULL THEN o.first_response_due_at "
            + "ELSE LEAST(o.first_response_due_at, o.resolution_due_at) END "
            + "ELSE CASE WHEN o.next_response_due_at IS NULL THEN o.resolution_due_at "
            + "WHEN o.resolution_due_at IS NULL THEN o.next_response_due_at "
            + "ELSE LEAST(o.next_response_due_at, o.resolution_due_at) END END ELSE NULL END IS NULL, "
            + "CASE WHEN o.sla_state IN ('ACTIVE', 'BREACHED') THEN CASE "
            + "WHEN o.first_responded_at IS NULL THEN COALESCE(LEAST(o.first_response_due_at, o.resolution_due_at), "
            + "o.first_response_due_at, o.resolution_due_at) ELSE COALESCE(LEAST(o.next_response_due_at, "
            + "o.resolution_due_at), o.next_response_due_at, o.resolution_due_at) END ELSE NULL END";
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
        mapWorkflow(resultSet, order, "");
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
        mapWorkflow(resultSet, order, "");
        order.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        return order;
    }

    private String summaryColumns() {
        return "o.order_id, o.user_id, o.item_id, o.amount, o.status AS order_status, "
            + "o.assigned_admin_id, o.transfer_request_id, o.transfer_requested_by, "
            + "o.transfer_target_admin_id, o.transfer_reason, o.transfer_requested_at, "
            + "o.reminder_count, o.last_reminded_at, o.sla_policy_id, o.first_response_due_at, "
            + "o.next_response_due_at, o.resolution_due_at, o.first_responded_at, "
            + "o.last_admin_response_at, o.resolved_at, o.sla_state, o.sla_paused_at, "
            + "o.sla_pause_reason, o.total_sla_paused_minutes, o.reopen_deadline_at, o.reopen_count, "
            + "o.workflow_version, "
            + "o.created_at AS order_created_at, i.title, i.category_id, i.status AS item_status, "
            + "i.created_at AS item_created_at, i.updated_at AS item_updated_at, c.name AS category_name, u.username";
    }

    private void mapWorkflow(java.sql.ResultSet resultSet, Order order, String prefix) throws java.sql.SQLException {
        order.setAssignedAdminId(nullableLong(resultSet, prefix + "assigned_admin_id"));
        order.setTransferRequestId(resultSet.getString(prefix + "transfer_request_id"));
        order.setTransferRequestedBy(nullableLong(resultSet, prefix + "transfer_requested_by"));
        order.setTransferTargetAdminId(nullableLong(resultSet, prefix + "transfer_target_admin_id"));
        order.setTransferReason(resultSet.getString(prefix + "transfer_reason"));
        Timestamp transferAt = resultSet.getTimestamp(prefix + "transfer_requested_at");
        order.setTransferRequestedAt(transferAt == null ? null : transferAt.toLocalDateTime());
        order.setReminderCount(resultSet.getInt(prefix + "reminder_count"));
        Timestamp remindedAt = resultSet.getTimestamp(prefix + "last_reminded_at");
        order.setLastRemindedAt(remindedAt == null ? null : remindedAt.toLocalDateTime());
        order.setSlaPolicyId(nullableLong(resultSet, prefix + "sla_policy_id"));
        order.setFirstResponseDueAt(nullableTime(resultSet, prefix + "first_response_due_at"));
        order.setNextResponseDueAt(nullableTime(resultSet, prefix + "next_response_due_at"));
        order.setResolutionDueAt(nullableTime(resultSet, prefix + "resolution_due_at"));
        order.setFirstRespondedAt(nullableTime(resultSet, prefix + "first_responded_at"));
        order.setLastAdminResponseAt(nullableTime(resultSet, prefix + "last_admin_response_at"));
        order.setResolvedAt(nullableTime(resultSet, prefix + "resolved_at"));
        order.setSlaState(resultSet.getString(prefix + "sla_state"));
        order.setSlaPausedAt(nullableTime(resultSet, prefix + "sla_paused_at"));
        order.setSlaPauseReason(resultSet.getString(prefix + "sla_pause_reason"));
        order.setTotalSlaPausedMinutes(resultSet.getInt(prefix + "total_sla_paused_minutes"));
        order.setReopenDeadlineAt(nullableTime(resultSet, prefix + "reopen_deadline_at"));
        order.setReopenCount(resultSet.getInt(prefix + "reopen_count"));
        order.setWorkflowVersion(resultSet.getLong(prefix + "workflow_version"));
    }

    private LocalDateTime nullableTime(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT); else statement.setLong(index, value);
    }

    private void setNullableTimestamp(PreparedStatement statement, int index, LocalDateTime value)
            throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP); else statement.setTimestamp(index, Timestamp.valueOf(value));
    }

    private Order findOne(Connection connection, String sql, Long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapOrder(resultSet) : null;
            }
        }
    }
}
