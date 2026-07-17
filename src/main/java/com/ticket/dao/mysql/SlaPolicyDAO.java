package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.model.SlaPolicy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;

public class SlaPolicyDAO extends BaseDAO {
    public SlaPolicyDAO() { super(); }
    SlaPolicyDAO(DataSource dataSource) { super(dataSource); }

    public SlaPolicy findActiveByPriority(Connection connection, String priority) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM sla_policies WHERE priority = ? AND enabled = 1 LIMIT 1")) {
            statement.setString(1, priority);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    public SlaPolicy findById(Connection connection, Long policyId) throws Exception {
        if (policyId == null) return null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM sla_policies WHERE policy_id = ? AND enabled = 1")) {
            statement.setLong(1, policyId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    private SlaPolicy map(java.sql.ResultSet resultSet) throws Exception {
        SlaPolicy policy = new SlaPolicy();
        policy.setPolicyId(resultSet.getLong("policy_id"));
        policy.setPolicyName(resultSet.getString("policy_name"));
        policy.setPriority(resultSet.getString("priority"));
        policy.setFirstResponseMinutes(resultSet.getInt("first_response_minutes"));
        policy.setNextResponseMinutes(resultSet.getInt("next_response_minutes"));
        policy.setResolutionMinutes(resultSet.getInt("resolution_minutes"));
        policy.setBusinessHoursOnly(resultSet.getInt("business_hours_only") == 1);
        return policy;
    }
}
