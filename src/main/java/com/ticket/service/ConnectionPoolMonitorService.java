package com.ticket.service;

import com.ticket.dto.ConnectionPoolStatusDTO;
import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class ConnectionPoolMonitorService {
    private final AuditLogService auditLogService = new AuditLogService();

    public ConnectionPoolStatusDTO currentStatus(User actor) {
        UserService.requireAdmin(actor);
        return MySQLDBUtil.getPoolStatus();
    }

    public List<ConnectionPoolStatusDTO> currentStatuses(User actor) {
        UserService.requireAdmin(actor);
        return MySQLDBUtil.getAllPoolStatuses();
    }

    public void recordPanelView(User actor) {
        UserService.requireAdmin(actor);
        auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO",
            "查看连接池监控面板", "VIEW_CONNECTION_POOL");
    }

    public void simulateConnectionUsage(User actor, int requestedConnections, int holdSeconds) {
        UserService.requireAdmin(actor);
        ConnectionPoolStatusDTO status = MySQLDBUtil.getPoolStatus();
        int safeCapacity = status.getMaximumPoolSize() - status.getActiveConnections() - 1;
        if (safeCapacity <= 0) {
            throw new BusinessException("写连接池可用余量不足，暂不执行模拟占用");
        }
        int connectionCount = Math.max(1, Math.min(requestedConnections, safeCapacity));
        int normalizedSeconds = Math.max(1, Math.min(holdSeconds, 30));
        List<Connection> connections = new ArrayList<>();
        try {
            for (int index = 0; index < connectionCount; index++) {
                Connection connection = MySQLDBUtil.getWriteConnection();
                try (PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
                    statement.executeQuery();
                }
                connections.add(connection);
            }
            Thread.sleep(normalizedSeconds * 1000L);
            auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO",
                "模拟占用连接：" + connectionCount + " 个，持续 " + normalizedSeconds + " 秒", "SIMULATE_CONNECTION_USAGE");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            throw new BusinessException("模拟占用连接失败", ex);
        } finally {
            for (Connection connection : connections) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // Ignore close errors during demo cleanup.
                }
            }
        }
    }
}
