package com.ticket.dao.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticket.model.Notification;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceManagementDAOTest {
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:service_management;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection()) {
            sql(connection, "DROP ALL OBJECTS");
            sql(connection, "CREATE TABLE users (user_id BIGINT PRIMARY KEY, username VARCHAR(50), role VARCHAR(20), status INT)");
            sql(connection, "CREATE TABLE profiles (user_id BIGINT PRIMARY KEY, notification_preference VARCHAR(20))");
            sql(connection, "CREATE TABLE categories (category_id BIGINT PRIMARY KEY, name VARCHAR(50))");
            sql(connection, "CREATE TABLE items (item_id BIGINT PRIMARY KEY)");
            sql(connection, "CREATE TABLE orders (order_id BIGINT PRIMARY KEY, assigned_admin_id BIGINT, status INT)");
            sql(connection, "CREATE TABLE ticket_assignment_rules (rule_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "rule_name VARCHAR(100), category_id BIGINT, priority VARCHAR(20), strategy VARCHAR(30), "
                + "target_admin_id BIGINT, enabled INT, sort_order INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            sql(connection, "CREATE TABLE notifications (notification_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "user_id BIGINT, item_id BIGINT, notification_type VARCHAR(50), title VARCHAR(160), "
                + "content VARCHAR(500), dedup_key VARCHAR(160), read_at TIMESTAMP, deleted_at TIMESTAMP, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE(user_id, dedup_key))");
            sql(connection, "CREATE TABLE ticket_ratings (rating_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "item_id BIGINT, user_id BIGINT, event_id VARCHAR(36), rating INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE(item_id, user_id), UNIQUE(event_id))");
            sql(connection, "INSERT INTO users VALUES (1, 'user01', 'USER', 1), "
                + "(10, 'admin01', 'ADMIN', 1), (11, 'admin02', 'ADMIN', 1), "
                + "(90, 'root01', 'ROOT', 1), (91, 'root02', 'ROOT', 0)");
            sql(connection, "INSERT INTO profiles VALUES (1, 'STATUS')");
            sql(connection, "INSERT INTO categories VALUES (100, '账号问题')");
            sql(connection, "INSERT INTO items VALUES (200)");
            sql(connection, "INSERT INTO orders VALUES (300, 10, 0)");
        }
    }

    @Test
    void resolvesMostSpecificRuleAndBalancesOpenWork() throws Exception {
        AssignmentRuleDAO dao = new AssignmentRuleDAO(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            sql(connection, "INSERT INTO ticket_assignment_rules "
                + "(rule_name, category_id, priority, strategy, enabled, sort_order) VALUES "
                + "('兜底', NULL, NULL, 'LEAST_LOADED', 1, 1000), "
                + "('紧急账号', 100, 'URGENT', 'LEAST_LOADED', 1, 10)");
            AssignmentRuleDAO.Assignment result = dao.resolve(connection, 100L, "URGENT");
            assertEquals("紧急账号", result.ruleName());
            assertEquals(11L, result.adminId(), "管理员 11 没有待办，应被优先选择");
        }
    }

    @Test
    void deduplicatesNotificationsAndPersistsReadState() throws Exception {
        NotificationDAO dao = new NotificationDAO(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            dao.insert(connection, 1L, 200L, "STATUS_CHANGED", "状态变化", "已处理中", "status:200:1");
            dao.insert(connection, 1L, 200L, "STATUS_CHANGED", "状态变化", "已处理中", "status:200:1");
            assertEquals("STATUS", dao.preference(connection, 1L));
            assertEquals(List.of(90L), dao.findActiveRootIds(connection));
        }
        assertEquals(1, dao.countUnread(1L));
        List<Notification> notifications = dao.findRecent(1L, 10);
        assertEquals(1, notifications.size());
        assertEquals("状态变化", notifications.get(0).getTitle());
        dao.markAllRead(1L);
        assertEquals(0, dao.countUnread(1L));
        try (Connection connection = dataSource.getConnection()) {
            dao.insert(connection, 1L, 200L, "CUSTOMER_REPLY", "客户回复", "请继续处理", "reply:200:2");
        }
        assertEquals(1, dao.deleteRead(1L));
        assertEquals(1, dao.countUnread(1L));
        Notification unread = dao.findRecent(1L, 10).get(0);
        assertEquals(0, dao.deleteOwned(10L, unread.getNotificationId()), "不能删除其他账号的通知");
        assertEquals(1, dao.deleteOwned(1L, unread.getNotificationId()));
        assertEquals(0, dao.findRecent(1L, 10).size());
    }

    @Test
    void ratingReservationIsUniquePerTicketAndUser() throws Exception {
        TicketRatingDAO dao = new TicketRatingDAO(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(dao.reserve(connection, 200L, 1L, "event-1", 5));
            assertFalse(dao.reserve(connection, 200L, 1L, "event-2", 4));
        }
    }

    private void sql(Connection connection, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(value)) {
            statement.execute();
        }
    }
}
