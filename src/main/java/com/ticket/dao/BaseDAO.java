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
    protected DataSource dataSource() {
        return MySQLDBUtil.getDataSource();
    }

    protected Connection getConnection() throws SQLException {
        return dataSource().getConnection();
    }

    protected <T> List<T> query(String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) {
        try (Connection connection = getConnection();
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
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.accept(statement);
            }
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DBException("MySQL update failed", ex);
        }
    }

    protected <T> T executeTransactionCallback(TransactionCallback<T> callback) {
        try (Connection connection = getConnection()) {
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
