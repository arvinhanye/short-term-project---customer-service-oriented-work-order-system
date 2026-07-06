package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.exception.DBException;
import com.ticket.model.Category;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

public class CategoryDAO extends BaseDAO {
    public List<Category> findAll() {
        return query("SELECT * FROM categories ORDER BY COALESCE(parent_id, category_id), category_id", null, this::mapCategory);
    }

    public Category findById(Long categoryId) {
        return queryOne("SELECT * FROM categories WHERE category_id = ?",
            statement -> statement.setLong(1, categoryId), this::mapCategory);
    }

    public long insert(Category category) {
        return executeTransactionCallback(connection -> insert(connection, category));
    }

    public long insert(Connection connection, Category category) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO categories (name, parent_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            if (category.getParentId() == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, category.getParentId());
            }
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DBException("Failed to get generated category_id");
        }
    }

    public void update(Category category) {
        update("UPDATE categories SET name = ?, parent_id = ? WHERE category_id = ?",
            statement -> {
                statement.setString(1, category.getName());
                if (category.getParentId() == null) {
                    statement.setNull(2, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(2, category.getParentId());
                }
                statement.setLong(3, category.getCategoryId());
            });
    }

    public void delete(Long categoryId) {
        update("DELETE FROM categories WHERE category_id = ?", statement -> statement.setLong(1, categoryId));
    }

    public int countChildren(Long categoryId) {
        Integer count = queryOne("SELECT COUNT(*) AS cnt FROM categories WHERE parent_id = ?",
            statement -> statement.setLong(1, categoryId), rs -> rs.getInt("cnt"));
        return count == null ? 0 : count;
    }

    public int countItems(Long categoryId) {
        Integer count = queryOne("SELECT COUNT(*) AS cnt FROM items WHERE category_id = ?",
            statement -> statement.setLong(1, categoryId), rs -> rs.getInt("cnt"));
        return count == null ? 0 : count;
    }

    private Category mapCategory(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Category category = new Category();
        category.setCategoryId(resultSet.getLong("category_id"));
        category.setName(resultSet.getString("name"));
        Object parentValue = resultSet.getObject("parent_id");
        category.setParentId(parentValue == null ? null : resultSet.getLong("parent_id"));
        return category;
    }
}
