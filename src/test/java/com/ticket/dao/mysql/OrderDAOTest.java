package com.ticket.dao.mysql;

import com.ticket.model.Order;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderDAOTest {
    private JdbcDataSource dataSource;
    private OrderDAO orderDAO;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:order_dao_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection()) {
            executeSql(connection, "DROP TABLE IF EXISTS orders");
            executeSql(connection, "DROP TABLE IF EXISTS items");
            executeSql(connection, "DROP TABLE IF EXISTS categories");
            executeSql(connection, "DROP TABLE IF EXISTS users");
            executeSql(connection, "CREATE TABLE users (user_id BIGINT PRIMARY KEY, username VARCHAR(50) NOT NULL)");
            executeSql(connection, "CREATE TABLE categories (category_id BIGINT PRIMARY KEY, name VARCHAR(50) NOT NULL)");
            executeSql(connection, """
                CREATE TABLE items (
                    item_id BIGINT PRIMARY KEY,
                    title VARCHAR(200) NOT NULL,
                    category_id BIGINT NOT NULL,
                    status TINYINT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
            executeSql(connection, """
                CREATE TABLE orders (
                    order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    item_id BIGINT NOT NULL,
                    amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
                    status TINYINT NOT NULL DEFAULT 0,
                    assigned_admin_id BIGINT NULL,
                    transfer_request_id VARCHAR(36) NULL,
                    transfer_requested_by BIGINT NULL,
                    transfer_target_admin_id BIGINT NULL,
                    transfer_reason VARCHAR(200) NULL,
                    transfer_requested_at TIMESTAMP NULL,
                    reminder_count INT NOT NULL DEFAULT 0,
                    last_reminded_at TIMESTAMP NULL,
                    sla_policy_id BIGINT NULL,
                    first_response_due_at TIMESTAMP NULL,
                    next_response_due_at TIMESTAMP NULL,
                    resolution_due_at TIMESTAMP NULL,
                    first_responded_at TIMESTAMP NULL,
                    last_admin_response_at TIMESTAMP NULL,
                    resolved_at TIMESTAMP NULL,
                    sla_state VARCHAR(20) NULL,
                    sla_paused_at TIMESTAMP NULL,
                    sla_pause_reason VARCHAR(100) NULL,
                    total_sla_paused_minutes INT NOT NULL DEFAULT 0,
                    reopen_deadline_at TIMESTAMP NULL,
                    reopen_count INT NOT NULL DEFAULT 0,
                    workflow_version BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            executeSql(connection, "CREATE INDEX idx_orders_user_status_created_at ON orders (user_id, status, created_at)");
            executeSql(connection, "CREATE INDEX idx_orders_status_created_at ON orders (status, created_at)");
        }

        orderDAO = new OrderDAO(dataSource);
    }

    @Test
    void shouldPageOrdersWithAndWithoutStatusFilter() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, 10001L, 2001L, 0, LocalDateTime.of(2026, 7, 1, 10, 0));
            insert(connection, 10001L, 2002L, 1, LocalDateTime.of(2026, 7, 2, 10, 0));
            insert(connection, 10001L, 2003L, 1, LocalDateTime.of(2026, 7, 3, 10, 0));
            insert(connection, 10002L, 2004L, 1, LocalDateTime.of(2026, 7, 4, 10, 0));
        }

        var allForUser = orderDAO.pageByUserAndStatus(10001L, null, 1, 20);
        Assertions.assertEquals(3, allForUser.getTotal());
        Assertions.assertEquals(2003L, allForUser.getRecords().get(0).getItemId());

        var processingForUser = orderDAO.pageByUserAndStatus(10001L, 1, 1, 20);
        Assertions.assertEquals(2, processingForUser.getTotal());
        Assertions.assertTrue(processingForUser.getRecords().stream().allMatch(order -> order.getStatus() == 1));

        var allProcessing = orderDAO.pageAllByStatus(1, 1, 2);
        Assertions.assertEquals(3, allProcessing.getTotal());
        Assertions.assertEquals(2, allProcessing.getRecords().size());
    }

    @Test
    void shouldUseOldStatusAsOptimisticLock() throws Exception {
        long orderId;
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, 10001L, 2101L, 0, LocalDateTime.of(2026, 7, 5, 10, 0));
            Order unassigned = orderDAO.findByItemIdForUpdate(connection, 2101L);
            orderId = unassigned.getOrderId();
            Assertions.assertEquals(1, orderDAO.claimIfUnassigned(connection, unassigned, 9001L));

            Order assigned = orderDAO.findByItemIdForUpdate(connection, 2101L);
            Assertions.assertEquals(1, orderDAO.updateStatusIfCurrent(connection, assigned, 9001L, 1));
            Assertions.assertEquals(0, orderDAO.updateStatusIfCurrent(connection, assigned, 9001L, 4));
        }

        Assertions.assertEquals(1, orderDAO.findById(orderId).getStatus());
    }

    @Test
    void shouldKeepSlaBreachedWhenCompletingAfterMissedFirstResponse() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, 10001L, 2150L, 1, LocalDateTime.now().minusHours(3));
            executeSql(connection, "UPDATE orders SET assigned_admin_id = 9001, sla_state = 'ACTIVE', "
                + "first_response_due_at = DATEADD('HOUR', -2, CURRENT_TIMESTAMP), "
                + "resolution_due_at = DATEADD('HOUR', 2, CURRENT_TIMESTAMP) WHERE item_id = 2150");
            Order current = orderDAO.findByItemIdForUpdate(connection, 2150L);
            Assertions.assertEquals(1, orderDAO.updateStatusIfCurrent(connection, current, 9001L, 2));
        }

        Order completed = orderDAO.findByItemId(2150L);
        Assertions.assertEquals("BREACHED", completed.getSlaState());
        Assertions.assertNotNull(completed.getResolvedAt());
    }

    @Test
    void shouldNotTurnMetResolutionIntoBreachWhenTicketIsClosedLater() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, 10001L, 2160L, 2, LocalDateTime.now().minusDays(2));
            executeSql(connection, "UPDATE orders SET assigned_admin_id = 9001, sla_state = 'MET', "
                + "resolved_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP), "
                + "resolution_due_at = DATEADD('HOUR', -1, CURRENT_TIMESTAMP) WHERE item_id = 2160");
            Order current = orderDAO.findByItemIdForUpdate(connection, 2160L);
            Assertions.assertEquals(1, orderDAO.updateStatusIfCurrent(connection, current, 9001L, 3));
        }

        Assertions.assertEquals("MET", orderDAO.findByItemId(2160L).getSlaState());
    }

    @Test
    void shouldPushAssignmentFiltersIntoPagedSql() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            executeSql(connection, "INSERT INTO users VALUES (10001, 'user01')");
            executeSql(connection, "INSERT INTO categories VALUES (4001, '账号问题')");
            for (long itemId = 2201L; itemId <= 2203L; itemId++) {
                executeSql(connection, "INSERT INTO items VALUES (" + itemId + ", '工单" + itemId
                    + "', 4001, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                insert(connection, 10001L, itemId, 0, LocalDateTime.of(2026, 7, 6, 10, 0));
            }
            executeSql(connection, "UPDATE orders SET assigned_admin_id = 9001 WHERE item_id = 2202");
            executeSql(connection, "UPDATE orders SET assigned_admin_id = 9002, transfer_target_admin_id = 9001, "
                + "transfer_request_id = 'request-2203' WHERE item_id = 2203");
        }

        var unassigned = orderDAO.pageTicketSummariesByAssignment(null, null, "UNASSIGNED", null, 1, 20);
        var mine = orderDAO.pageTicketSummariesByAssignment(null, null, "ASSIGNED_TO", 9001L, 1, 20);
        var pending = orderDAO.pageTicketSummariesByAssignment(null, null, "PENDING_TRANSFER_TO", 9001L, 1, 20);
        var pendingByDisplayStatus = orderDAO.pageTicketSummariesByAssignment(
            99, null, "PENDING_TRANSFER_TO", 9001L, 1, 20);
        var allPendingByDisplayStatus = orderDAO.pageTicketSummaries(null, 99, null, 1, 20);

        Assertions.assertEquals(1, unassigned.getTotal());
        Assertions.assertEquals(2201L, unassigned.getRecords().get(0).getItem().getItemId());
        Assertions.assertEquals(1, mine.getTotal());
        Assertions.assertEquals(2202L, mine.getRecords().get(0).getItem().getItemId());
        Assertions.assertEquals(1, pending.getTotal());
        Assertions.assertEquals(2203L, pending.getRecords().get(0).getItem().getItemId());
        Assertions.assertEquals(1, pendingByDisplayStatus.getTotal());
        Assertions.assertEquals(1, allPendingByDisplayStatus.getTotal());
        Assertions.assertEquals(0, allPendingByDisplayStatus.getRecords().get(0).getOrder().getStatus(),
            "展示状态 99 不应覆盖数据库中的原生命周期状态");
    }

    private void insert(Connection connection, Long userId, Long itemId, int status, LocalDateTime createdAt) throws Exception {
        Order order = new Order();
        order.setUserId(userId);
        order.setItemId(itemId);
        order.setAmount(BigDecimal.ZERO);
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        orderDAO.insert(connection, order);
    }

    private void executeSql(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
