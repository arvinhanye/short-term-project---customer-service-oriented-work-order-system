package com.ticket.service;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class MaintenanceService {
    private final AuditLogService auditLogService = new AuditLogService();

    public int batchUpdateOrderStatus(User actor, int oldStatus, int newStatus, LocalDateTime beforeTime) {
        UserService.requireAdmin(actor);
        if (beforeTime == null || beforeTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException("批处理截止时间不合法");
        }
        BusinessService.validateStatusTransition(oldStatus, newStatus);
        try (Connection connection = MySQLDBUtil.getWriteConnection();
             CallableStatement statement = connection.prepareCall("{call sp_batch_update_order_status(?, ?, ?)}")) {
            statement.setInt(1, oldStatus);
            statement.setInt(2, newStatus);
            statement.setTimestamp(3, Timestamp.valueOf(beforeTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                int affectedRows = resultSet.next() ? resultSet.getInt("affected_rows") : 0;
                auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO",
                    "批量更新工单状态，影响 " + affectedRows + " 行", "BATCH_UPDATE_STATUS");
                return affectedRows;
            }
        } catch (Exception ex) {
            auditLogService.write(String.valueOf(actor.getUserId()), "DB_ERROR", "ERROR",
                "批量更新工单状态失败", "BATCH_UPDATE_STATUS");
            throw new BusinessException("批量更新工单状态失败", ex);
        }
    }
}
