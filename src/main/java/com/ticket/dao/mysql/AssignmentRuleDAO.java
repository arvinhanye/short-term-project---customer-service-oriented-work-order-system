package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.model.AssignmentRule;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.ArrayList;
import javax.sql.DataSource;

public class AssignmentRuleDAO extends BaseDAO {
    public record Assignment(Long adminId, Long ruleId, String ruleName, String strategy) { }

    public AssignmentRuleDAO() { super(); }
    AssignmentRuleDAO(DataSource dataSource) { super(dataSource); }

    public Assignment resolve(Connection connection, Long categoryId, String priority) throws Exception {
        String sql = "SELECT rule_id, rule_name, strategy, target_admin_id FROM ticket_assignment_rules "
            + "WHERE enabled = 1 AND (category_id IS NULL OR category_id = ?) "
            + "AND (priority IS NULL OR priority = ?) "
            + "ORDER BY (CASE WHEN category_id IS NOT NULL THEN 1 ELSE 0 END "
            + "+ CASE WHEN priority IS NOT NULL THEN 1 ELSE 0 END) DESC, sort_order, rule_id";
        List<RuleCandidate> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (categoryId == null) statement.setNull(1, Types.BIGINT); else statement.setLong(1, categoryId);
            statement.setString(2, priority);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    candidates.add(new RuleCandidate(resultSet.getLong("rule_id"),
                        resultSet.getString("rule_name"), resultSet.getString("strategy"),
                        nullableLong(resultSet, "target_admin_id")));
                }
            }
        }
        for (RuleCandidate candidate : candidates) {
            Long adminId = "SPECIFIC_ADMIN".equals(candidate.strategy())
                ? activeAdmin(connection, candidate.targetAdminId()) : leastLoadedAdmin(connection);
            if (adminId != null) return new Assignment(adminId, candidate.ruleId(),
                candidate.ruleName(), candidate.strategy());
        }
        return null;
    }

    public List<AssignmentRule> findAll() {
        String sql = "SELECT r.*, c.name AS category_name, u.username AS target_admin_name "
            + "FROM ticket_assignment_rules r LEFT JOIN categories c ON c.category_id = r.category_id "
            + "LEFT JOIN users u ON u.user_id = r.target_admin_id ORDER BY r.sort_order, r.rule_id";
        return query(sql, null, resultSet -> {
            AssignmentRule rule = new AssignmentRule();
            rule.setRuleId(resultSet.getLong("rule_id"));
            rule.setRuleName(resultSet.getString("rule_name"));
            rule.setCategoryId(nullableLong(resultSet, "category_id"));
            rule.setCategoryName(resultSet.getString("category_name"));
            rule.setPriority(resultSet.getString("priority"));
            rule.setStrategy(resultSet.getString("strategy"));
            rule.setTargetAdminId(nullableLong(resultSet, "target_admin_id"));
            rule.setTargetAdminName(resultSet.getString("target_admin_name"));
            rule.setEnabled(resultSet.getInt("enabled") == 1);
            rule.setSortOrder(resultSet.getInt("sort_order"));
            return rule;
        });
    }

    public void insert(AssignmentRule rule) {
        update("INSERT INTO ticket_assignment_rules (rule_name, category_id, priority, strategy, target_admin_id, "
                + "enabled, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)", statement -> {
            statement.setString(1, rule.getRuleName());
            if (rule.getCategoryId() == null) statement.setNull(2, Types.BIGINT);
            else statement.setLong(2, rule.getCategoryId());
            statement.setString(3, rule.getPriority());
            statement.setString(4, rule.getStrategy());
            if (rule.getTargetAdminId() == null) statement.setNull(5, Types.BIGINT);
            else statement.setLong(5, rule.getTargetAdminId());
            statement.setInt(6, rule.isEnabled() ? 1 : 0);
            statement.setInt(7, rule.getSortOrder());
        });
    }

    public void setEnabled(Long ruleId, boolean enabled) {
        update("UPDATE ticket_assignment_rules SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE rule_id = ?",
            statement -> {
                statement.setInt(1, enabled ? 1 : 0);
                statement.setLong(2, ruleId);
            });
    }

    private Long activeAdmin(Connection connection, Long userId) throws Exception {
        if (userId == null) return null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id FROM users WHERE user_id = ? AND role = 'ADMIN' AND status = 1")) {
            statement.setLong(1, userId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private Long leastLoadedAdmin(Connection connection) throws Exception {
        String sql = "SELECT u.user_id FROM users u LEFT JOIN orders o "
            + "ON o.assigned_admin_id = u.user_id AND o.status IN (0, 1, 5, 6) "
            + "WHERE u.role = 'ADMIN' AND u.status = 1 GROUP BY u.user_id "
            + "ORDER BY COUNT(o.order_id), u.user_id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : null;
        }
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record RuleCandidate(Long ruleId, String ruleName, String strategy, Long targetAdminId) { }
}
