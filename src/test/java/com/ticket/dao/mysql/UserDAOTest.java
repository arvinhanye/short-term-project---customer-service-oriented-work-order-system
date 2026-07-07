package com.ticket.dao.mysql;

import com.ticket.model.User;
import com.ticket.util.PasswordUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserDAOTest {
    private UserDAO userDAO;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:user_dao_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection()) {
            executeSql(connection, "DROP TABLE IF EXISTS users");
            executeSql(connection, """
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    email VARCHAR(100) NOT NULL UNIQUE,
                    phone VARCHAR(20),
                    role VARCHAR(10) NOT NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        userDAO = new UserDAO(dataSource);
    }

    @Test
    void shouldCreateReadUpdateAndDeleteUser() {
        User user = buildUser("alice", "alice@ticket.local", "13900001111", "USER", 1);

        long userId = userDAO.insert(user);
        user.setUserId(userId);

        User byId = userDAO.findById(userId);
        Assertions.assertEquals("alice", byId.getUsername());
        Assertions.assertEquals("alice@ticket.local", userDAO.findByUsername("alice").getEmail());
        Assertions.assertEquals("13900001111", userDAO.findByEmail("alice@ticket.local").getPhone());
        Assertions.assertEquals(1, userDAO.findByRole("USER").size());
        Assertions.assertEquals(1, userDAO.findByStatus(1).size());

        user.setUsername("alice_admin");
        user.setEmail("alice.admin@ticket.local");
        user.setPhone("13800002222");
        user.setRole("ADMIN");
        user.setStatus(0);
        String newHash = PasswordUtil.hashPassword("Ticket@456");
        user.setPasswordHash(newHash);
        Assertions.assertEquals(1, userDAO.update(user));

        User updated = userDAO.findById(userId);
        Assertions.assertEquals("alice_admin", updated.getUsername());
        Assertions.assertEquals("ADMIN", updated.getRole());
        Assertions.assertEquals(0, updated.getStatus());
        Assertions.assertEquals(newHash, updated.getPasswordHash());

        updated.setEmail("alice.final@ticket.local");
        updated.setPhone("13700003333");
        userDAO.updateBasicInfo(updated);
        userDAO.updateStatus(userId, 1);
        String finalHash = PasswordUtil.hashPassword("Ticket@789");
        Assertions.assertEquals(1, userDAO.updatePasswordHash(userId, finalHash));

        User finalUser = userDAO.findById(userId);
        Assertions.assertEquals("alice.final@ticket.local", finalUser.getEmail());
        Assertions.assertEquals("13700003333", finalUser.getPhone());
        Assertions.assertEquals(1, finalUser.getStatus());
        Assertions.assertEquals(finalHash, finalUser.getPasswordHash());

        Assertions.assertEquals(1, userDAO.findAll().size());
        Assertions.assertEquals(1, userDAO.deleteById(userId));
        Assertions.assertNull(userDAO.findById(userId));
    }

    @Test
    void shouldHashAndVerifyPassword() {
        String password = "Ticket@123";
        String hash = PasswordUtil.hashPassword(password);
        Assertions.assertNotEquals(password, hash);
        Assertions.assertTrue(PasswordUtil.matches(password, hash));
    }

    private User buildUser(String username, String email, String phone, String role, int status) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.hashPassword("Ticket@123"));
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(LocalDateTime.of(2026, 7, 7, 8, 30));
        user.setUpdatedAt(LocalDateTime.of(2026, 7, 7, 8, 30));
        return user;
    }

    private void executeSql(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
