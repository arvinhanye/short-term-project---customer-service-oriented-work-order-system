package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.exception.DBException;
import com.ticket.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;

public class UserDAO extends BaseDAO {
    public UserDAO() {
        super();
    }

    UserDAO(DataSource dataSource) {
        super(dataSource);
    }

    public long insert(User user) {
        return executeTransactionCallback(connection -> insert(connection, user));
    }

    public User findByUsername(String username) {
        return queryOne("SELECT * FROM users WHERE username = ?",
            statement -> statement.setString(1, username), this::mapUser);
    }

    public User findByEmail(String email) {
        return queryOne("SELECT * FROM users WHERE email = ?",
            statement -> statement.setString(1, email), this::mapUser);
    }

    public User findById(Long userId) {
        return queryOne("SELECT * FROM users WHERE user_id = ?",
            statement -> statement.setLong(1, userId), this::mapUser);
    }

    public List<User> findAll() {
        return query("SELECT * FROM users ORDER BY user_id DESC", null, this::mapUser);
    }

    public List<User> findByRole(String role) {
        return query("SELECT * FROM users WHERE role = ? ORDER BY user_id DESC",
            statement -> statement.setString(1, role), this::mapUser);
    }

    public List<User> findByStatus(int status) {
        return query("SELECT * FROM users WHERE status = ? ORDER BY user_id DESC",
            statement -> statement.setInt(1, status), this::mapUser);
    }

    public long insert(Connection connection, User user) throws Exception {
        String sql = "INSERT INTO users (username, password_hash, email, phone, role, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime createdAt = user.getCreatedAt() == null ? now : user.getCreatedAt();
            LocalDateTime updatedAt = user.getUpdatedAt() == null ? now : user.getUpdatedAt();
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getEmail());
            statement.setString(4, user.getPhone());
            statement.setString(5, user.getRole());
            statement.setInt(6, user.getStatus());
            statement.setTimestamp(7, Timestamp.valueOf(createdAt));
            statement.setTimestamp(8, Timestamp.valueOf(updatedAt));
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DBException("Failed to get generated user_id");
        }
    }

    public int update(User user) {
        return update("UPDATE users SET username = ?, password_hash = ?, email = ?, phone = ?, role = ?, status = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            statement -> {
                statement.setString(1, user.getUsername());
                statement.setString(2, user.getPasswordHash());
                statement.setString(3, user.getEmail());
                statement.setString(4, user.getPhone());
                statement.setString(5, user.getRole());
                statement.setInt(6, user.getStatus());
                statement.setLong(7, user.getUserId());
            });
    }

    public void updateBasicInfo(User user) {
        update("UPDATE users SET email = ?, phone = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            statement -> {
                statement.setString(1, user.getEmail());
                statement.setString(2, user.getPhone());
                statement.setLong(3, user.getUserId());
            });
    }

    public int updatePasswordHash(Long userId, String passwordHash) {
        return update("UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            statement -> {
                statement.setString(1, passwordHash);
                statement.setLong(2, userId);
            });
    }

    public void updateStatus(Long userId, int status) {
        update("UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            statement -> {
                statement.setInt(1, status);
                statement.setLong(2, userId);
            });
    }

    public int deleteById(Long userId) {
        return update("DELETE FROM users WHERE user_id = ?",
            statement -> statement.setLong(1, userId));
    }

    private User mapUser(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        User user = new User();
        user.setUserId(resultSet.getLong("user_id"));
        user.setUsername(resultSet.getString("username"));
        user.setPasswordHash(resultSet.getString("password_hash"));
        user.setEmail(resultSet.getString("email"));
        user.setPhone(resultSet.getString("phone"));
        user.setRole(resultSet.getString("role"));
        user.setStatus(resultSet.getInt("status"));
        user.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        user.setUpdatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime());
        return user;
    }
}
