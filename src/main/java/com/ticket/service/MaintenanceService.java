package com.ticket.service;

import com.ticket.dao.mongo.SystemLogDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.SystemLog;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;

public class MaintenanceService {
    private final SystemLogDAO systemLogDAO = new SystemLogDAO();

    public int batchUpdateOrderStatus(User actor, int oldStatus, int newStatus, LocalDateTime beforeTime) {
        UserService.requireAdmin(actor);
        if (beforeTime == null || beforeTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException("批处理截止时间不合法");
        }
        BusinessService.validateStatusTransition(oldStatus, newStatus);
        try (Connection connection = MySQLDBUtil.getDataSource().getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_batch_update_order_status(?, ?, ?)}")) {
            statement.setInt(1, oldStatus);
            statement.setInt(2, newStatus);
            statement.setTimestamp(3, Timestamp.valueOf(beforeTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                int affectedRows = resultSet.next() ? resultSet.getInt("affected_rows") : 0;
                writeSystemLog(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO",
                    "批量更新工单状态，影响 " + affectedRows + " 行", "BATCH_UPDATE_STATUS");
                return affectedRows;
            }
        } catch (Exception ex) {
            writeSystemLog(String.valueOf(actor.getUserId()), "DB_ERROR", "ERROR",
                "批量更新工单状态失败", "BATCH_UPDATE_STATUS");
            throw new BusinessException("批量更新工单状态失败", ex);
        }
    }

    private void writeSystemLog(String userId, String logType, String level, String message, String operation) {
        SystemLog log = new SystemLog();
        log.setUserId(userId);
        log.setLogType(logType);
        log.setLogLevel(level);
        log.setMessage(message);
        SystemLog.ActionDetail actionDetail = new SystemLog.ActionDetail();
        actionDetail.setIp("127.0.0.1");
        actionDetail.setOperation(operation);
        log.setActionDetail(actionDetail);
        log.setTimestamp(Instant.now());
        systemLogDAO.insert(log);
    }
}
