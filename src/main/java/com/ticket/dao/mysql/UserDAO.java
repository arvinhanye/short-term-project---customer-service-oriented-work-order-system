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
    private static final String PUBLIC_COLUMNS = "user_id, username, email, phone, role, status, "
        + "failed_login_attempts, locked_until, must_change_password, password_changed_at, created_at, updated_at";
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

    public User findByUsernameForAuthentication(String username) {
        return queryOneOnWrite("SELECT * FROM users WHERE username = ?",
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

    public User findByIdForSecurity(Long userId) {
        return queryOneOnWrite("SELECT * FROM users WHERE user_id = ?",
            statement -> statement.setLong(1, userId), this::mapUser);
    }

    public List<User> findAll() {
        return query("SELECT * FROM users ORDER BY user_id DESC", null, this::mapUser);
    }

    /** 管理列表不读取密码哈希，减少传输与敏感数据在内存中的停留。 */
    public List<User> findAllPublic() {
        return query("SELECT " + PUBLIC_COLUMNS + " FROM users ORDER BY user_id DESC",
            null, this::mapPublicUser);
    }

    /** 工单分配只查询启用的 ADMIN，避免读取整张用户表后在 Java 中过滤。 */
    public List<User> findActiveAdminsPublic() {
        return query("SELECT " + PUBLIC_COLUMNS
                + " FROM users WHERE role = 'ADMIN' AND status = 1 ORDER BY user_id DESC",
            null, this::mapPublicUser);
    }

    public List<User> findByRole(String role) {
        return query("SELECT * FROM users WHERE role = ? ORDER BY user_id DESC",
            statement -> statement.setString(1, role), this::mapUser);
    }

    public List<User> findByStatus(int status) {
        return query("SELECT * FROM users WHERE status = ? ORDER BY user_id DESC",
            statement -> statement.setInt(1, status), this::mapUser);
    }

    public long countActiveByRole(String role) {
        Long count = queryOneOnWrite("SELECT COUNT(*) AS total FROM users WHERE role = ? AND status = 1",
            statement -> statement.setString(1, role), resultSet -> resultSet.getLong("total"));
        return count == null ? 0 : count;
    }

    public long insert(Connection connection, User user) throws Exception {
        String sql = "INSERT INTO users (username, password_hash, email, phone, role, status, failed_login_attempts, "
            + "locked_until, must_change_password, password_changed_at, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            statement.setInt(7, user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts());
            setNullableTimestamp(statement, 8, user.getLockedUntil());
            statement.setInt(9, user.getMustChangePassword() == null ? 0 : user.getMustChangePassword());
            setNullableTimestamp(statement, 10, user.getPasswordChangedAt());
            statement.setTimestamp(11, Timestamp.valueOf(createdAt));
            statement.setTimestamp(12, Timestamp.valueOf(updatedAt));
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

    public int updateLoginSecurity(Long userId, int failedAttempts, LocalDateTime lockedUntil) {
        return update("UPDATE users SET failed_login_attempts = ?, locked_until = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ?",
            statement -> {
                statement.setInt(1, failedAttempts);
                setNullableTimestamp(statement, 2, lockedUntil);
                statement.setLong(3, userId);
            });
    }

    /** 单条 SQL 原子递增失败次数，避免并发登录尝试覆盖彼此的计数。 */
    public User recordFailedLogin(Long userId, int maxAttempts, int lockMinutes) {
        update("UPDATE users SET failed_login_attempts = failed_login_attempts + 1, "
                + "locked_until = CASE WHEN failed_login_attempts + 1 >= ? "
                + "THEN TIMESTAMPADD(MINUTE, ?, CURRENT_TIMESTAMP) ELSE NULL END, "
                + "updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            statement -> {
                statement.setInt(1, maxAttempts);
                statement.setInt(2, lockMinutes);
                statement.setLong(3, userId);
            });
        return findByIdForSecurity(userId);
    }

    public int updatePasswordSecurity(Long userId, String passwordHash, boolean mustChangePassword,
                                      LocalDateTime changedAt) {
        return update("UPDATE users SET password_hash = ?, must_change_password = ?, password_changed_at = ?, "
                + "failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            statement -> {
                statement.setString(1, passwordHash);
                statement.setInt(2, mustChangePassword ? 1 : 0);
                setNullableTimestamp(statement, 3, changedAt);
                statement.setLong(4, userId);
            });
    }

    public void updateStatus(Long userId, int status) {
        update("UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            statement -> {
                statement.setInt(1, status);
                statement.setLong(2, userId);
            });
    }

    /**
     * 在同一事务内锁定治理账号并更新状态，避免并发操作禁用最后一个有效 ROOT。
     * 返回 -1 表示触发最后 ROOT 保护，0 表示目标不存在，1 表示更新成功。
     */
    public int updateStatusWithRootProtection(Long userId, int status) {
        return updateStatusWithRootProtection(userId, status, null);
    }

    public int updateStatusWithRootProtection(Long userId, int status, String expectedRole) {
        return executeTransactionCallback(connection -> {
            int activeRoots = status == 0 && "ROOT".equals(expectedRole)
                ? lockAndCountActiveRoots(connection) : -1;
            User target = findByIdForUpdate(connection, userId);
            if (target == null) {
                return 0;
            }
            if (expectedRole != null && !expectedRole.equals(target.getRole())) {
                return -2;
            }
            if (status == 0 && "ROOT".equals(target.getRole()) && Integer.valueOf(1).equals(target.getStatus())) {
                if ((activeRoots < 0 ? lockAndCountActiveRoots(connection) : activeRoots) <= 1) {
                    return -1;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?")) {
                statement.setInt(1, status);
                statement.setLong(2, userId);
                return statement.executeUpdate();
            }
        });
    }

    /** 与状态更新使用相同的行锁策略，避免并发降级最后一个有效 ROOT。 */
    public int updateRoleWithRootProtection(Long userId, String newRole) {
        return updateRoleWithRootProtection(userId, newRole, null);
    }

    public int updateRoleWithRootProtection(Long userId, String newRole, String expectedRole) {
        return executeTransactionCallback(connection -> {
            int activeRoots = "ROOT".equals(expectedRole) && !"ROOT".equals(newRole)
                ? lockAndCountActiveRoots(connection) : -1;
            User target = findByIdForUpdate(connection, userId);
            if (target == null) {
                return 0;
            }
            if (expectedRole != null && !expectedRole.equals(target.getRole())) {
                return -2;
            }
            if ("ROOT".equals(target.getRole()) && !"ROOT".equals(newRole)
                    && Integer.valueOf(1).equals(target.getStatus())
                    && (activeRoots < 0 ? lockAndCountActiveRoots(connection) : activeRoots) <= 1) {
                return -1;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET role = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?")) {
                statement.setString(1, newRole);
                statement.setLong(2, userId);
                return statement.executeUpdate();
            }
        });
    }

    public int deleteById(Long userId) {
        return update("DELETE FROM users WHERE user_id = ?",
            statement -> statement.setLong(1, userId));
    }

    private User mapUser(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        User user = mapPublicUser(resultSet);
        user.setPasswordHash(resultSet.getString("password_hash"));
        return user;
    }

    private User mapPublicUser(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        User user = new User();
        user.setUserId(resultSet.getLong("user_id"));
        user.setUsername(resultSet.getString("username"));
        user.setEmail(resultSet.getString("email"));
        user.setPhone(resultSet.getString("phone"));
        user.setRole(resultSet.getString("role"));
        user.setStatus(resultSet.getInt("status"));
        user.setFailedLoginAttempts(resultSet.getInt("failed_login_attempts"));
        Timestamp lockedUntil = resultSet.getTimestamp("locked_until");
        user.setLockedUntil(lockedUntil == null ? null : lockedUntil.toLocalDateTime());
        user.setMustChangePassword(resultSet.getInt("must_change_password"));
        Timestamp passwordChangedAt = resultSet.getTimestamp("password_changed_at");
        user.setPasswordChangedAt(passwordChangedAt == null ? null : passwordChangedAt.toLocalDateTime());
        user.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        user.setUpdatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime());
        return user;
    }

    private User findByIdForUpdate(Connection connection, Long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM users WHERE user_id = ? FOR UPDATE")) {
            statement.setLong(1, userId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapUser(resultSet) : null;
            }
        }
    }

    private int lockAndCountActiveRoots(Connection connection) throws Exception {
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id FROM users WHERE role = 'ROOT' AND status = 1 FOR UPDATE");
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                count++;
            }
        }
        return count;
    }

    private void setNullableTimestamp(PreparedStatement statement, int index, LocalDateTime value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.valueOf(value));
        }
    }
}
