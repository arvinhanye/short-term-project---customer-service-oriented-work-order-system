package com.ticket.service;

import com.ticket.dao.mongo.CommentDAO;
import com.ticket.dao.mongo.LogDAO;
import com.ticket.dao.mongo.SystemLogDAO;
import com.ticket.dto.ReportDTO;
import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.model.ServiceMetrics;
import com.ticket.util.MySQLDBUtil;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.sql.PreparedStatement;
import org.bson.Document;

public class StatisticsService {
    private final LogDAO logDAO = new LogDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final SystemLogDAO systemLogDAO = new SystemLogDAO();

    public List<ReportDTO> monthlyReport(User actor, int year, int month) {
        UserService.requireAdmin(actor);
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new BusinessException("报表年月不合法");
        }
        List<ReportDTO> report = new ArrayList<>();
        try (Connection connection = MySQLDBUtil.getReadConnection();
             CallableStatement statement = connection.prepareCall("{call sp_monthly_report(?, ?)}")) {
            statement.setInt(1, year);
            statement.setInt(2, month);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    report.add(new ReportDTO("total_count", resultSet.getLong("total_count"), resultSet.getBigDecimal("total_amount")));
                    report.add(new ReportDTO("pending_count", resultSet.getLong("pending_count"), null));
                    report.add(new ReportDTO("processing_count", resultSet.getLong("processing_count"), null));
                    report.add(new ReportDTO("completed_count", resultSet.getLong("completed_count"), null));
                    report.add(new ReportDTO("closed_count", resultSet.getLong("closed_count"), null));
                    report.add(new ReportDTO("cancelled_count", getOptionalLong(resultSet, "cancelled_count"), null));
                    report.add(new ReportDTO("avg_amount", 0L, resultSet.getBigDecimal("avg_amount")));
                }
            }
            return report;
        } catch (Exception ex) {
            throw new BusinessException("调用月度报表存储过程失败", ex);
        }
    }

    public ServiceMetrics serviceMetrics(User actor, LocalDateTime from, LocalDateTime to) {
        UserService.requireAdmin(actor);
        LocalDateTime normalizedFrom = from == null ? LocalDateTime.now().minusDays(30) : from;
        LocalDateTime normalizedTo = to == null ? LocalDateTime.now() : to;
        if (!normalizedFrom.isBefore(normalizedTo)) throw new BusinessException("报表起始时间必须早于结束时间");
        String sql = "SELECT COUNT(*) ticket_count, "
            + "COALESCE(AVG(CASE WHEN first_responded_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND, created_at, first_responded_at) / 60.0 END), 0) avg_first, "
            + "COALESCE(AVG(CASE WHEN resolved_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND, created_at, resolved_at) / 60.0 END), 0) avg_resolution, "
            + "COALESCE(100.0 * SUM(sla_state = 'MET') / NULLIF(SUM(sla_state IN ('MET','BREACHED')), 0), 0) compliance, "
            + "COALESCE(AVG(CASE WHEN status IN (0,1,5,6) THEN TIMESTAMPDIFF(SECOND, created_at, CURRENT_TIMESTAMP(3)) / 3600.0 END), 0) avg_backlog, "
            + "COALESCE(100.0 * SUM(status IN (2,3) AND reopen_count = 0) / NULLIF(SUM(status IN (2,3)), 0), 0) first_contact, "
            + "SUM(status IN (0,1,5,6)) open_backlog, SUM(sla_state = 'BREACHED') breached_count "
            + "FROM orders WHERE created_at >= ? AND created_at < ?";
        try (Connection connection = MySQLDBUtil.getReadConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, java.sql.Timestamp.valueOf(normalizedFrom));
            statement.setTimestamp(2, java.sql.Timestamp.valueOf(normalizedTo));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new ServiceMetrics(resultSet.getLong("ticket_count"), resultSet.getDouble("avg_first"),
                    resultSet.getDouble("avg_resolution"), resultSet.getDouble("compliance"),
                    resultSet.getDouble("avg_backlog"), resultSet.getDouble("first_contact"),
                    averageSatisfaction(connection, normalizedFrom, normalizedTo),
                    resultSet.getLong("open_backlog"), resultSet.getLong("breached_count"));
            }
        } catch (Exception ex) {
            throw new BusinessException("读取服务质量指标失败", ex);
        }
    }

    public List<Document> administratorLoad(User actor) {
        UserService.requireAdmin(actor);
        String sql = "SELECT u.user_id, u.username, COUNT(o.order_id) total_assigned, "
            + "SUM(o.status IN (0,1,5,6)) open_count, SUM(o.sla_state = 'BREACHED') breached_count, "
            + "COALESCE(AVG(CASE WHEN o.status IN (0,1,5,6) THEN TIMESTAMPDIFF(SECOND, o.created_at, CURRENT_TIMESTAMP(3)) / 3600.0 END), 0) avg_backlog_hours "
            + "FROM users u LEFT JOIN orders o ON o.assigned_admin_id = u.user_id "
            + "WHERE u.role IN ('ADMIN','ROOT') AND u.status = 1 GROUP BY u.user_id, u.username "
            + "ORDER BY open_count DESC, breached_count DESC, u.user_id";
        List<Document> result = new ArrayList<>();
        try (Connection connection = MySQLDBUtil.getReadConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.add(new Document("user_id", rs.getLong("user_id"))
                    .append("username", rs.getString("username"))
                    .append("total_assigned", rs.getLong("total_assigned"))
                    .append("open_count", rs.getLong("open_count"))
                    .append("breached_count", rs.getLong("breached_count"))
                    .append("avg_backlog_hours", rs.getDouble("avg_backlog_hours")));
            }
            return result;
        } catch (Exception ex) {
            throw new BusinessException("读取管理员负载失败", ex);
        }
    }

    public List<Document> satisfactionTrend(User actor, int days) {
        UserService.requireAdmin(actor);
        int normalizedDays = Math.max(1, Math.min(days, 365));
        String sql = "SELECT DATE(created_at) day, ROUND(AVG(rating), 2) average_rating, COUNT(*) rating_count "
            + "FROM ticket_ratings WHERE created_at >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) "
            + "GROUP BY DATE(created_at) ORDER BY day";
        List<Document> result = new ArrayList<>();
        try (Connection connection = MySQLDBUtil.getReadConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, normalizedDays);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(new Document("day", rs.getDate("day").toLocalDate().toString())
                    .append("average_rating", rs.getDouble("average_rating"))
                    .append("rating_count", rs.getLong("rating_count")));
            }
            return result;
        } catch (Exception ex) {
            throw new BusinessException("读取满意度趋势失败", ex);
        }
    }

    private double averageSatisfaction(Connection connection, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(AVG(rating), 0) value FROM ticket_ratings WHERE created_at >= ? AND created_at < ?")) {
            statement.setTimestamp(1, java.sql.Timestamp.valueOf(from));
            statement.setTimestamp(2, java.sql.Timestamp.valueOf(to));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getDouble("value");
            }
        }
    }

    private long getOptionalLong(ResultSet resultSet, String columnName) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            if (columnName.equalsIgnoreCase(metaData.getColumnLabel(index))) {
                return resultSet.getLong(columnName);
            }
        }
        return 0L;
    }

    public List<Document> hotItems(User actor) {
        UserService.requireAdmin(actor);
        return logDAO.aggregateHotItems();
    }

    public List<Document> userActions(User actor) {
        UserService.requireAdmin(actor);
        return logDAO.aggregateUserActions();
    }

    public List<Document> actionTypeSummary(User actor) {
        UserService.requireAdmin(actor);
        return logDAO.aggregateActionTypeSummary();
    }

    public List<Document> dailyActionTrend(User actor, int days) {
        UserService.requireAdmin(actor);
        return logDAO.aggregateDailyActions(days);
    }

    public List<Document> clientUsage(User actor) {
        UserService.requireAdmin(actor);
        return logDAO.aggregateClientUsage();
    }

    public List<Document> recentActionLogs(User actor, int limit) {
        UserService.requireAdmin(actor);
        return logDAO.findRecent(limit);
    }

    public List<Document> recentActionLogsByUser(User actor, Long userId, int limit) {
        UserService.requireAdmin(actor);
        return logDAO.findRecentByUser(String.valueOf(userId), limit);
    }

    public List<Document> actionLogsByItem(User actor, Long itemId, int limit) {
        UserService.requireAdmin(actor);
        return logDAO.findByItemId(String.valueOf(itemId), limit);
    }

    public List<Document> ratingStats(User actor) {
        UserService.requireAdmin(actor);
        return commentDAO.aggregateAverageRatingByItem();
    }

    public List<Document> commentTagSummary(User actor) {
        UserService.requireAdmin(actor);
        return commentDAO.aggregateTagSummary();
    }

    public List<Document> ratingDistribution(User actor) {
        UserService.requireAdmin(actor);
        return commentDAO.aggregateRatingDistribution();
    }

    public List<Document> commentTrend(User actor, int days) {
        UserService.requireAdmin(actor);
        return commentDAO.aggregateCommentTrend(days);
    }

    public List<Document> itemCommentStats(User actor) {
        UserService.requireAdmin(actor);
        return commentDAO.aggregateItemCommentStats();
    }

    public Document behaviorDashboard(User actor) {
        UserService.requireAdmin(actor);
        return new Document("action_type_summary", logDAO.aggregateActionTypeSummary())
            .append("daily_action_trend", logDAO.aggregateDailyActions(30))
            .append("hot_items", logDAO.aggregateHotItems())
            .append("user_actions", logDAO.aggregateUserActions())
            .append("client_usage", logDAO.aggregateClientUsage())
            .append("recent_actions", logDAO.findRecent(20))
            .append("comment_tag_summary", commentDAO.aggregateTagSummary())
            .append("rating_distribution", commentDAO.aggregateRatingDistribution())
            .append("item_comment_stats", commentDAO.aggregateItemCommentStats());
    }

    public List<Document> auditLogs(User actor, int limit) {
        UserService.requireAdmin(actor);
        return systemLogDAO.findRecent(limit);
    }

    public List<Document> auditLogs(User actor, String logType, String logLevel, String userId, String keyword, int limit) {
        UserService.requireAdmin(actor);
        return systemLogDAO.findByCondition(logType, logLevel, userId, keyword, limit);
    }

    public List<Document> systemLogSummary(User actor) {
        UserService.requireAdmin(actor);
        return systemLogDAO.aggregateByLogType();
    }

    public Document systemLogDashboard(User actor) {
        UserService.requireAdmin(actor);
        return new Document("type_summary", systemLogDAO.aggregateByLogType())
            .append("level_summary", systemLogDAO.aggregateByLogLevel())
            .append("user_summary", systemLogDAO.aggregateByUser(10))
            .append("daily_trend", systemLogDAO.aggregateDailyTrend(30))
            .append("recent_logs", systemLogDAO.findRecent(20));
    }

    public List<Document> systemLogLevelSummary(User actor) {
        UserService.requireAdmin(actor);
        return systemLogDAO.aggregateByLogLevel();
    }

    public List<Document> systemLogUserSummary(User actor, int limit) {
        UserService.requireAdmin(actor);
        return systemLogDAO.aggregateByUser(limit);
    }

    public List<Document> systemLogDailyTrend(User actor, int days) {
        UserService.requireAdmin(actor);
        return systemLogDAO.aggregateDailyTrend(days);
    }
}
