package com.ticket.dao.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryDAOTest {
    private JdbcDataSource dataSource;
    private CategoryDAO categoryDAO;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:category_dao_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "DROP TABLE IF EXISTS items");
            execute(connection, "DROP TABLE IF EXISTS categories");
            execute(connection, """
                CREATE TABLE categories (
                    category_id BIGINT PRIMARY KEY,
                    name VARCHAR(50) NOT NULL,
                    parent_id BIGINT NULL
                )
                """);
            execute(connection, """
                CREATE TABLE items (
                    item_id BIGINT PRIMARY KEY,
                    category_id BIGINT NOT NULL
                )
                """);
            execute(connection, "INSERT INTO categories VALUES (4001, '账号问题', NULL)");
            execute(connection, "INSERT INTO categories VALUES (4002, '登录失败', 4001)");
            execute(connection, "INSERT INTO categories VALUES (4003, '重复名称', NULL)");
            execute(connection, "INSERT INTO items VALUES (2001, 4001)");
            execute(connection, "INSERT INTO items VALUES (2002, 4002)");
            execute(connection, "INSERT INTO items VALUES (2003, 4002)");
        }
        categoryDAO = new CategoryDAO(dataSource);
    }

    @Test
    void shouldLoadHierarchyAndTicketImpactInOneOverview() {
        var overview = categoryDAO.findManagementOverview();
        var root = overview.stream().filter(category -> category.getCategoryId() == 4001L).findFirst().orElseThrow();
        var child = overview.stream().filter(category -> category.getCategoryId() == 4002L).findFirst().orElseThrow();

        Assertions.assertEquals(1, root.getChildCount());
        Assertions.assertEquals(1, root.getDirectTicketCount());
        Assertions.assertEquals(3, root.getTotalTicketCount());
        Assertions.assertEquals(1, root.getLevel());
        Assertions.assertEquals("账号问题", child.getParentName());
        Assertions.assertEquals(2, child.getDirectTicketCount());
        Assertions.assertEquals(2, child.getTotalTicketCount());
        Assertions.assertEquals(2, child.getLevel());
    }

    @Test
    void shouldCheckDuplicateNameOnlyAmongSiblings() {
        Assertions.assertTrue(categoryDAO.existsSiblingName(null, null, " 账号问题 "));
        Assertions.assertFalse(categoryDAO.existsSiblingName(4001L, null, "账号问题"));
        Assertions.assertFalse(categoryDAO.existsSiblingName(null, 4001L, "账号问题"));
        Assertions.assertTrue(categoryDAO.existsSiblingName(null, 4001L, " 登录失败 "));
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
