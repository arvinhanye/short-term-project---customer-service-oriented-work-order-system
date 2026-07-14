package com.ticket.util;

import com.ticket.config.DBConfig;
import com.ticket.dto.ConnectionPoolStatusDTO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MySQLDBUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLDBUtil.class);
    private static HikariDataSource writeDataSource;
    private static HikariDataSource readDataSource;

    private MySQLDBUtil() {
    }

    public static synchronized DataSource getDataSource() {
        return getWriteDataSource();
    }

    public static synchronized DataSource getWriteDataSource() {
        if (writeDataSource == null || writeDataSource.isClosed()) {
            writeDataSource = createDataSource("WRITE");
        }
        return writeDataSource;
    }

    public static synchronized DataSource getReadDataSource() {
        if (readDataSource == null || readDataSource.isClosed()) {
            readDataSource = createDataSource("READ");
        }
        return readDataSource;
    }

    public static Connection getWriteConnection() throws SQLException {
        return getConnectionWithReconnect("WRITE");
    }

    public static Connection getReadConnection() throws SQLException {
        return getConnectionWithReconnect("READ");
    }

    public static synchronized ConnectionPoolStatusDTO getPoolStatus() {
        return getPoolStatus("WRITE");
    }

    public static synchronized ConnectionPoolStatusDTO getPoolStatus(String role) {
        HikariDataSource hikariDataSource = "READ".equalsIgnoreCase(role)
            ? (HikariDataSource) getReadDataSource()
            : (HikariDataSource) getWriteDataSource();
        HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
        ConnectionPoolStatusDTO status = new ConnectionPoolStatusDTO();
        status.setRole("READ".equalsIgnoreCase(role) ? "READ" : "WRITE");
        status.setPoolName(hikariDataSource.getPoolName());
        status.setMaximumPoolSize(hikariDataSource.getMaximumPoolSize());
        status.setMinimumIdle(hikariDataSource.getMinimumIdle());
        status.setConnectionTimeoutMs(hikariDataSource.getConnectionTimeout());
        status.setIdleTimeoutMs(hikariDataSource.getIdleTimeout());
        status.setMaxLifetimeMs(hikariDataSource.getMaxLifetime());
        status.setLeakDetectionThresholdMs(hikariDataSource.getLeakDetectionThreshold());
        if (poolBean != null) {
            status.setActiveConnections(poolBean.getActiveConnections());
            status.setIdleConnections(poolBean.getIdleConnections());
            status.setTotalConnections(poolBean.getTotalConnections());
            status.setThreadsAwaitingConnection(poolBean.getThreadsAwaitingConnection());
        }
        return status;
    }

    public static synchronized List<ConnectionPoolStatusDTO> getAllPoolStatuses() {
        List<ConnectionPoolStatusDTO> statuses = new ArrayList<>();
        statuses.add(getPoolStatus("WRITE"));
        statuses.add(getPoolStatus("READ"));
        return statuses;
    }

    public static synchronized void reconnectReadDataSource() {
        closeDataSource(readDataSource, "READ");
        readDataSource = createDataSource("READ");
    }

    public static synchronized void reconnectWriteDataSource() {
        closeDataSource(writeDataSource, "WRITE");
        writeDataSource = createDataSource("WRITE");
    }

    public static boolean isConnectionException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLRecoverableException
                || current instanceof SQLTransientConnectionException
                || current instanceof SQLNonTransientConnectionException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("08")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static synchronized void close() {
        closeDataSource(readDataSource, "READ");
        closeDataSource(writeDataSource, "WRITE");
        readDataSource = null;
        writeDataSource = null;
    }

    private static synchronized Connection getConnectionWithReconnect(String role) throws SQLException {
        try {
            return dataSource(role).getConnection();
        } catch (SQLException ex) {
            if (!isConnectionException(ex)) {
                throw ex;
            }
            LOGGER.warn("{} datasource connection failed, rebuilding HikariCP pool and retrying once", role, ex);
            if ("READ".equalsIgnoreCase(role)) {
                reconnectReadDataSource();
            } else {
                reconnectWriteDataSource();
            }
            return dataSource(role).getConnection();
        }
    }

    private static synchronized HikariDataSource dataSource(String role) {
        return "READ".equalsIgnoreCase(role)
            ? (HikariDataSource) getReadDataSource()
            : (HikariDataSource) getWriteDataSource();
    }

    private static HikariDataSource createDataSource(String role) {
        HikariConfig config = new HikariConfig();
        boolean readRole = "READ".equalsIgnoreCase(role);
        String jdbcUrl = configValue(readRole ? "mysql.read.url" : "mysql.write.url", DBConfig.get("mysql.url"));
        String username = configValue(readRole ? "mysql.read.username" : "mysql.write.username", DBConfig.get("mysql.username"));
        String password = configValue(readRole ? "mysql.read.password" : "mysql.write.password", DBConfig.get("mysql.password"));
        validateCredentialConfiguration(role, username, password);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(DBConfig.getInt(readRole ? "mysql.read.pool.maximumPoolSize" : "mysql.write.pool.maximumPoolSize",
            DBConfig.getInt("mysql.pool.maximumPoolSize", 10)));
        config.setMinimumIdle(DBConfig.getInt(readRole ? "mysql.read.pool.minimumIdle" : "mysql.write.pool.minimumIdle",
            DBConfig.getInt("mysql.pool.minimumIdle", 2)));
        config.setConnectionTimeout(DBConfig.getInt("mysql.pool.connectionTimeoutMs", 30000));
        config.setIdleTimeout(DBConfig.getInt("mysql.pool.idleTimeoutMs", 600000));
        config.setMaxLifetime(DBConfig.getInt("mysql.pool.maxLifetimeMs", 1800000));
        config.setLeakDetectionThreshold(DBConfig.getInt("mysql.pool.leakDetectionThresholdMs", 0));
        config.setPoolName(readRole ? "ticket-management-read-pool" : "ticket-management-write-pool");
        HikariDataSource dataSource = new HikariDataSource(config);
        LOGGER.info("Initialized {} HikariCP datasource: {}", role, config.getJdbcUrl());
        return dataSource;
    }

    private static String configValue(String key, String defaultValue) {
        String value = DBConfig.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static void validateCredentialConfiguration(String role, String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(role + " 数据库凭据未配置；请设置 TICKET_MYSQL_USERNAME/TICKET_MYSQL_PASSWORD，"
                + "或分别设置 TICKET_MYSQL_" + role + "_USERNAME/TICKET_MYSQL_" + role + "_PASSWORD");
        }
        if ("root".equalsIgnoreCase(username.trim())) {
            throw new IllegalStateException("应用禁止使用 MySQL root 账号，请配置最小权限的专用数据库账号");
        }
    }

    private static void closeDataSource(HikariDataSource dataSource, String role) {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Closed {} HikariCP datasource", role);
        }
    }
}
