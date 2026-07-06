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
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

public class StatisticsService {
    private final LogDAO logDAO = new LogDAO();
    private final CommentDAO commentDAO = new CommentDAO();
    private final SystemLogDAO systemLogDAO = new SystemLogDAO();

    public List<ReportDTO> monthlyReport(User actor, int year, int month) {
        UserService.requireAdmin(actor);
        List<ReportDTO> report = new ArrayList<>();
        try (Connection connection = MySQLDBUtil.getDataSource().getConnection();
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
                }
            }
            return report;
        } catch (Exception ex) {
            throw new BusinessException("调用月度报表存储过程失败", ex);
        }
    }

    public List<Document> hotItems(User actor) {
        UserService.requireAdmin(actor);
        return logDAO.aggregateHotItems();
    }

    public List<Document> userActions(User actor) {
        UserService.requireAdmin(actor);
        return logDAO.aggregateUserActions();
    }

    public List<Document> ratingStats(User actor) {
        UserService.requireAdmin(actor);
        return commentDAO.aggregateAverageRatingByItem();
    }

    public List<Document> auditLogs(User actor, int limit) {
        UserService.requireAdmin(actor);
        return systemLogDAO.findRecent(limit);
    }

    public List<Document> systemLogSummary(User actor) {
        UserService.requireAdmin(actor);
        return systemLogDAO.aggregateByLogType();
    }
}
