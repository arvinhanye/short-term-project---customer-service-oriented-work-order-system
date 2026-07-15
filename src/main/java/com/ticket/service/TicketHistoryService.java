package com.ticket.service;

import com.ticket.dao.mysql.OrderDAO;
import com.ticket.dao.mysql.TicketHistoryDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Order;
import com.ticket.model.TicketHistory;
import com.ticket.model.User;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TicketHistoryService {
    public static final String PUBLIC = "PUBLIC";
    public static final String STAFF_ONLY = "STAFF_ONLY";
    public static final String AUDIT_ONLY = "AUDIT_ONLY";

    private final TicketHistoryDAO historyDAO = new TicketHistoryDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    public void append(Connection connection, TicketHistory history) throws Exception {
        if (history.getEventId() == null || history.getEventId().isBlank()) {
            history.setEventId(UUID.randomUUID().toString());
        }
        if (history.getOccurredAt() == null) {
            history.setOccurredAt(LocalDateTime.now());
        }
        if (history.getVisibility() == null || history.getVisibility().isBlank()) {
            history.setVisibility(STAFF_ONLY);
        }
        historyDAO.insert(connection, history);
    }

    public List<TicketHistory> listForTicket(User actor, Long itemId, int limit) {
        UserService.requireActiveUser(actor);
        Order order = orderDAO.findByItemId(itemId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        boolean staff = UserService.isTicketStaff(actor) || "ROOT".equals(actor.getRole());
        if (!staff && !actor.getUserId().equals(order.getUserId())) {
            throw new BusinessException("无权查看该工单历史");
        }
        List<TicketHistory> histories = historyDAO.findByItemId(itemId, staff, limit);
        if (!staff) {
            // PUBLIC 只表示事件可展示；客服身份、内部原因和载荷仍不能泄露给普通用户。
            histories.forEach(history -> {
                history.setActorUserId(null);
                history.setActorUsername(null);
                history.setActorRole(null);
                history.setTargetUserId(null);
                history.setFromAdminId(null);
                history.setToAdminId(null);
                history.setReason(null);
                history.setEventPayload(null);
            });
        }
        return histories;
    }

    public TicketHistory event(Order order, User actor, String eventType, String visibility, long eventSeq) {
        TicketHistory history = new TicketHistory();
        history.setEventId(UUID.randomUUID().toString());
        history.setItemId(order.getItemId());
        history.setOrderId(order.getOrderId());
        history.setEventSeq(eventSeq);
        history.setEventType(eventType);
        history.setVisibility(visibility);
        if (actor != null) {
            history.setActorUserId(actor.getUserId());
            history.setActorUsername(actor.getUsername());
            history.setActorRole(actor.getRole());
        }
        history.setOccurredAt(LocalDateTime.now());
        return history;
    }
}
