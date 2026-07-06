package com.ticket.util;

import com.ticket.config.DBConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MySQLDBUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLDBUtil.class);
    private static HikariDataSource dataSource;

    private MySQLDBUtil() {
    }

    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DBConfig.get("mysql.url"));
            config.setUsername(DBConfig.get("mysql.username"));
            config.setPassword(DBConfig.get("mysql.password"));
            config.setMaximumPoolSize(DBConfig.getInt("mysql.pool.maximumPoolSize", 10));
            config.setMinimumIdle(DBConfig.getInt("mysql.pool.minimumIdle", 2));
            config.setPoolName("ticket-management-pool");
            dataSource = new HikariDataSource(config);
            LOGGER.info("Initialized HikariCP datasource");
        }
        return dataSource;
    }

    public static synchronized void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            LOGGER.info("Closed HikariCP datasource");
        }
    }
}
