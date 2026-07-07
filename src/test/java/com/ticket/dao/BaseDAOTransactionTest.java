package com.ticket.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseDAOTransactionTest {
    private TransactionProbeDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:transaction_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection()) {
            executeSql(connection, "DROP TABLE IF EXISTS tx_probe");
            executeSql(connection, "CREATE TABLE tx_probe (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50) NOT NULL)");
        }
        dao = new TransactionProbeDAO(dataSource);
    }

    @Test
    void shouldRollbackTransactionWhenCallbackFails() {
        Assertions.assertThrows(RuntimeException.class, () -> dao.insertThenFail("rollback"));
        Assertions.assertEquals(0, dao.countRows());
    }

    @Test
    void shouldCommitTransactionWhenCallbackSucceeds() {
        dao.insert("commit");
        Assertions.assertEquals(1, dao.countRows());
    }

    private void executeSql(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private static class TransactionProbeDAO extends BaseDAO {
        TransactionProbeDAO(DataSource dataSource) {
            super(dataSource);
        }

        void insert(String name) {
            executeTransactionCallback(connection -> {
                insertRow(connection, name);
                return null;
            });
        }

        void insertThenFail(String name) {
            executeTransactionCallback(connection -> {
                insertRow(connection, name);
                throw new RuntimeException("force rollback");
            });
        }

        int countRows() {
            Integer count = queryOne("SELECT COUNT(*) AS cnt FROM tx_probe", null, resultSet -> resultSet.getInt("cnt"));
            return count == null ? 0 : count;
        }

        private void insertRow(Connection connection, String name) throws Exception {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tx_probe (name) VALUES (?)")) {
                statement.setString(1, name);
                statement.executeUpdate();
            }
        }
    }
}
