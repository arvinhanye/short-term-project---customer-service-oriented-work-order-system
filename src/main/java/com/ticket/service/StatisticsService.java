package com.ticket.service;

import com.ticket.dao.mongo.CommentDAO;
import com.ticket.dao.mongo.LogDAO;
import com.ticket.dao.mongo.SystemLogDAO;
import com.ticket.dto.ReportDTO;
import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
