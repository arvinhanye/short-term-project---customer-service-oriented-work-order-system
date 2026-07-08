package com.ticket.dao;

import com.ticket.exception.DBException;
import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public abstract class BaseDAO {
    private final DataSource dataSource;

    protected BaseDAO() {
        this(null);
    }

    protected BaseDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected DataSource dataSource() {
        return dataSource == null ? MySQLDBUtil.getWriteDataSource() : dataSource;
    }

    protected Connection getConnection() throws SQLException {
        return getWriteConnection();
    }

    protected Connection getReadConnection() throws SQLException {
        return dataSource == null ? MySQLDBUtil.getReadConnection() : dataSource.getConnection();
    }

    protected Connection getWriteConnection() throws SQLException {
        return dataSource == null ? MySQLDBUtil.getWriteConnection() : dataSource.getConnection();
    }

    protected <T> List<T> query(String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) {
        try {
            return queryOnce(sql, binder, mapper);
        } catch (DBException ex) {
            if (dataSource == null && MySQLDBUtil.isConnectionException(ex)) {
                MySQLDBUtil.reconnectReadDataSource();
                return queryOnce(sql, binder, mapper);
            }
            throw ex;
        }
    }

    private <T> List<T> queryOnce(String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) {
        try (Connection connection = getReadConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.accept(statement);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.map(resultSet));
                }
                return results;
            }
        } catch (SQLException ex) {
            throw new DBException("MySQL query failed", ex);
        }
    }

    protected <T> T queryOne(String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) {
        List<T> results = query(sql, binder, mapper);
        return results.isEmpty() ? null : results.get(0);
    }

    protected int update(String sql, SqlConsumer<PreparedStatement> binder) {
        return executeTransactionCallback(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (binder != null) {
                    binder.accept(statement);
                }
                return statement.executeUpdate();
            }
        });
    }

    protected <T> T executeTransactionCallback(TransactionCallback<T> callback) {
        try {
            return executeTransactionCallbackOnce(callback);
        } catch (DBException ex) {
            if (dataSource == null && MySQLDBUtil.isConnectionException(ex)) {
                MySQLDBUtil.reconnectWriteDataSource();
                return executeTransactionCallbackOnce(callback);
            }
            throw ex;
        }
    }

    private <T> T executeTransactionCallbackOnce(TransactionCallback<T> callback) {
        try (Connection connection = getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = callback.execute(connection);
                connection.commit();
                return result;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw ex instanceof DBException ? (DBException) ex : new DBException("Transaction failed", ex);
        }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer<T> {
        void accept(T target) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection connection) throws Exception;
    }
}
