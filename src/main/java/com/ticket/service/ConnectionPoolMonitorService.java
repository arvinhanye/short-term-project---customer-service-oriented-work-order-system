package com.ticket.service;

import com.ticket.dto.ConnectionPoolStatusDTO;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class ConnectionPoolMonitorService {
    public ConnectionPoolStatusDTO currentStatus(User actor) {
        UserService.requireAdmin(actor);
        return MySQLDBUtil.getPoolStatus();
    }

    public void simulateConnectionUsage(User actor, int requestedConnections, int holdSeconds) {
        UserService.requireAdmin(actor);
        ConnectionPoolStatusDTO status = MySQLDBUtil.getPoolStatus();
        int connectionCount = Math.max(1, Math.min(requestedConnections, Math.max(1, status.getMaximumPoolSize() - 1)));
        int normalizedSeconds = Math.max(1, Math.min(holdSeconds, 30));
        List<Connection> connections = new ArrayList<>();
        try {
            for (int index = 0; index < connectionCount; index++) {
                Connection connection = MySQLDBUtil.getDataSource().getConnection();
                try (PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
                    statement.executeQuery();
                }
                connections.add(connection);
            }
            Thread.sleep(normalizedSeconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            throw new com.ticket.exception.BusinessException("模拟占用连接失败", ex);
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
