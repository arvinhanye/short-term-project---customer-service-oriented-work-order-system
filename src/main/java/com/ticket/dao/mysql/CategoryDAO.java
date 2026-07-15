package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.dto.CategoryOverviewDTO;
import com.ticket.exception.DBException;
import com.ticket.model.Category;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;

public class CategoryDAO extends BaseDAO {
    public CategoryDAO() {
        super();
    }

    public CategoryDAO(DataSource dataSource) {
        super(dataSource);
    }

    public List<Category> findAll() {
        return query("SELECT * FROM categories ORDER BY COALESCE(parent_id, category_id), category_id", null, this::mapCategory);
    }

    /** 分类树一次查询返回父级、子分类数和工单影响范围，避免 UI 逐节点查询。 */
    public List<CategoryOverviewDTO> findManagementOverview() {
        String sql = "SELECT c.category_id, c.name, c.parent_id, p.name AS parent_name, "
            + "p.parent_id AS parent_parent_id, "
            + "(SELECT COUNT(*) FROM categories ch WHERE ch.parent_id = c.category_id) AS child_count, "
            + "(SELECT COUNT(*) FROM items di WHERE di.category_id = c.category_id) AS direct_ticket_count, "
            + "(SELECT COUNT(*) FROM items ti JOIN categories tc ON tc.category_id = ti.category_id "
            + " WHERE tc.category_id = c.category_id OR tc.parent_id = c.category_id) AS total_ticket_count "
            + "FROM categories c LEFT JOIN categories p ON p.category_id = c.parent_id "
            + "ORDER BY COALESCE(c.parent_id, c.category_id), c.parent_id IS NOT NULL, c.category_id";
        return query(sql, null, resultSet -> {
            CategoryOverviewDTO dto = new CategoryOverviewDTO();
            dto.setCategoryId(resultSet.getLong("category_id"));
            dto.setName(resultSet.getString("name"));
            dto.setParentId(nullableLong(resultSet, "parent_id"));
            dto.setParentName(resultSet.getString("parent_name"));
            dto.setParentParentId(nullableLong(resultSet, "parent_parent_id"));
            dto.setChildCount(resultSet.getInt("child_count"));
            dto.setDirectTicketCount(resultSet.getInt("direct_ticket_count"));
            dto.setTotalTicketCount(resultSet.getInt("total_ticket_count"));
            return dto;
        });
    }

    public boolean existsSiblingName(Long excludedCategoryId, Long parentId, String name) {
        Integer count = queryOne("SELECT COUNT(*) AS cnt FROM categories WHERE "
                + "(? IS NULL OR category_id <> ?) AND LOWER(TRIM(name)) = LOWER(TRIM(?)) "
                + "AND ((parent_id IS NULL AND ? IS NULL) OR parent_id = ?)",
            statement -> {
                if (excludedCategoryId == null) {
                    statement.setNull(1, java.sql.Types.BIGINT);
                    statement.setNull(2, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(1, excludedCategoryId);
                    statement.setLong(2, excludedCategoryId);
                }
                statement.setString(3, name);
                if (parentId == null) {
                    statement.setNull(4, java.sql.Types.BIGINT);
                    statement.setNull(5, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(4, parentId);
                    statement.setLong(5, parentId);
                }
            }, resultSet -> resultSet.getInt("cnt"));
        return count != null && count > 0;
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

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
