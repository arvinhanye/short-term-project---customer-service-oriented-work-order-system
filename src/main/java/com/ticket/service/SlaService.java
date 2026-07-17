package com.ticket.service;

import com.ticket.dao.mysql.SlaPolicyDAO;
import com.ticket.model.Order;
import com.ticket.model.SlaPolicy;
import com.ticket.util.BusinessTimeUtil;
import java.sql.Connection;
import java.time.LocalDateTime;

public class SlaService {
    public record ReopenTargets(LocalDateTime nextResponseDueAt, LocalDateTime resolutionDueAt) {
    }

    private final SlaPolicyDAO policyDAO = new SlaPolicyDAO();

    public void applyPolicy(Connection connection, Order order, String priority, LocalDateTime startedAt)
            throws Exception {
        SlaPolicy policy = policyDAO.findActiveByPriority(connection, priority);
        if (policy == null) return;
        order.setSlaPolicyId(policy.getPolicyId());
        order.setFirstResponseDueAt(BusinessTimeUtil.addMinutes(
            startedAt, policy.getFirstResponseMinutes(), policy.isBusinessHoursOnly()));
        order.setResolutionDueAt(BusinessTimeUtil.addMinutes(
            startedAt, policy.getResolutionMinutes(), policy.isBusinessHoursOnly()));
        order.setSlaState("ACTIVE");
    }

    public LocalDateTime nextResponseDueAt(Connection connection, Order order, LocalDateTime repliedAt)
            throws Exception {
        if (order == null || order.getFirstRespondedAt() == null || order.getSlaPolicyId() == null) return null;
        SlaPolicy policy = policyDAO.findById(connection, order.getSlaPolicyId());
        return policy == null ? null : BusinessTimeUtil.addMinutes(
            repliedAt, policy.getNextResponseMinutes(), policy.isBusinessHoursOnly());
    }

    public ReopenTargets reopenTargets(Connection connection, Order order, LocalDateTime reopenedAt)
            throws Exception {
        if (order == null || order.getSlaPolicyId() == null) return new ReopenTargets(null, null);
        SlaPolicy policy = policyDAO.findById(connection, order.getSlaPolicyId());
        if (policy == null) return new ReopenTargets(null, null);
        return new ReopenTargets(
            BusinessTimeUtil.addMinutes(reopenedAt, policy.getNextResponseMinutes(), policy.isBusinessHoursOnly()),
            BusinessTimeUtil.addMinutes(reopenedAt, policy.getResolutionMinutes(), policy.isBusinessHoursOnly()));
    }
}
