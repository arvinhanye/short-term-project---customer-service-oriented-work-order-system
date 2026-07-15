package com.ticket.service;

import com.ticket.dao.mysql.CategoryDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.dto.HealthCheckDTO;
import com.ticket.model.User;
import com.ticket.util.MongoDBUtil;
import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.bson.Document;

public class SystemHealthService {
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    public HealthCheckDTO runFullCheck(User actor) {
        UserService.requireAdministrator(actor);
        HealthCheckDTO result = new HealthCheckDTO();
        check(result, "MySQL 写库连接", this::checkMysqlWriteConnection);
        check(result, "MySQL 读库连接", this::checkMysqlReadConnection);
        check(result, "MongoDB 连接", this::checkMongoConnection);
        check(result, "分类 DAO 查询", () -> categoryDAO.findAll());
        check(result, "工单分页查询", () -> orderDAO.pageAllByStatus(null, 1, 5));
        return result;
    }

    private void checkMysqlWriteConnection() throws Exception {
        try (Connection connection = MySQLDBUtil.getWriteConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            statement.executeQuery();
        }
    }

    private void checkMysqlReadConnection() throws Exception {
        try (Connection connection = MySQLDBUtil.getReadConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            statement.executeQuery();
        }
    }

    private void checkMongoConnection() {
        MongoDBUtil.getDatabase().runCommand(new Document("ping", 1));
    }

    private void check(HealthCheckDTO result, String checkName, CheckedRunnable runnable) {
        long startedAt = System.nanoTime();
        try {
            runnable.run();
            result.pass(checkName, elapsedMillis(startedAt));
        } catch (Exception ex) {
            result.fail(checkName, ex, elapsedMillis(startedAt));
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
