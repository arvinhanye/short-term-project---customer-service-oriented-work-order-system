package com.ticket.service;

import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;

public class ServiceManagementSchemaService {
    public void verify() {
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            verifyQuery(connection, "SELECT sla_policy_id, sla_state, first_response_due_at, "
                + "next_response_due_at, resolution_due_at, first_responded_at, last_admin_response_at, "
                + "resolved_at FROM orders LIMIT 0");
            verifyQuery(connection, "SELECT notification_id, deleted_at FROM notifications LIMIT 0");
            verifyQuery(connection, "SELECT rule_id FROM ticket_assignment_rules LIMIT 0");
            verifyQuery(connection, "SELECT rating_id FROM ticket_ratings LIMIT 0");
            verifyQuery(connection, "SELECT policy_id FROM sla_policies LIMIT 0");
            verifyQuery(connection, "SELECT notification_preference FROM profiles LIMIT 0");
            verifyQuery(connection, "SELECT sla_paused_at, sla_pause_reason, total_sla_paused_minutes, "
                + "reopen_deadline_at, reopen_count FROM orders LIMIT 0");
            verifyQuery(connection, "SELECT article_id FROM knowledge_articles LIMIT 0");
            verifyQuery(connection, "SELECT template_id FROM reply_templates LIMIT 0");
            verifyQuery(connection, "SELECT macro_id FROM handling_macros LIMIT 0");
            verifyQuery(connection, "SELECT run_id FROM data_lifecycle_runs LIMIT 0");
        } catch (Exception ex) {
            throw new IllegalStateException("service management schema is missing", ex);
        }
    }

    private void verifyQuery(Connection connection, String sql) throws Exception {
        try (var statement = connection.prepareStatement(sql); var ignored = statement.executeQuery()) {
            // Successful preparation and execution are sufficient.
        }
    }
}
