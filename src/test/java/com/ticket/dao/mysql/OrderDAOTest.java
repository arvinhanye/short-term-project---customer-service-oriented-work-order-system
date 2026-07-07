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
            executeSql(connection, """
                CREATE TABLE orders (
                    order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    item_id BIGINT NOT NULL,
                    amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
                    status TINYINT NOT NULL DEFAULT 0,
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
