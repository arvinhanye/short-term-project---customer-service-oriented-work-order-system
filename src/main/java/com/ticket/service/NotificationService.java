package com.ticket.service;

import com.ticket.dao.mysql.NotificationDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.model.Notification;
import com.ticket.model.Order;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import com.ticket.util.SlaDisplayUtil;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class NotificationService {
    private static final int SLA_WARNING_MINUTES = 60;
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    public void notify(Connection connection, Long userId, Long itemId, String type,
                       String title, String content, String dedupKey) throws Exception {
        if (userId == null || !allows(notificationDAO.preference(connection, userId), type)) return;
        notificationDAO.insert(connection, userId, itemId, type, title, content, dedupKey);
    }

    private boolean allows(String preference, String type) {
        return switch (preference == null ? "ALL" : preference) {
            case "NONE" -> false;
            case "STATUS" -> type.equals("TICKET_CREATED") || type.equals("TICKET_CLAIMED")
                || type.equals("ASSIGNEE_CHANGED") || type.startsWith("STATUS_");
            case "RESULT" -> type.equals("STATUS_RESULT");
            default -> true;
        };
    }

    public List<Notification> recent(User actor, int limit) {
        UserService.requireActiveUser(actor);
        refreshSlaAlerts(actor);
        return notificationDAO.findRecent(actor.getUserId(), limit);
    }

    public long unreadCount(User actor) {
        UserService.requireActiveUser(actor);
        refreshSlaAlerts(actor);
        return notificationDAO.countUnread(actor.getUserId());
    }

    public void markAllRead(User actor) {
        UserService.requireActiveUser(actor);
        notificationDAO.markAllRead(actor.getUserId());
    }

    public boolean delete(User actor, Long notificationId) {
        UserService.requireActiveUser(actor);
        return notificationDAO.deleteOwned(actor.getUserId(), notificationId) == 1;
    }

    public int clearRead(User actor) {
        UserService.requireActiveUser(actor);
        return notificationDAO.deleteRead(actor.getUserId());
    }

    /** 登录或刷新通知时补发一小时内预警，并把已到期 SLA 原子标记为超时。 */
    public void refreshSlaAlerts(User actor) {
        if (actor == null || (!"ADMIN".equals(actor.getRole()) && !"ROOT".equals(actor.getRole()))) return;
        boolean rootView = "ROOT".equals(actor.getRole());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusMinutes(SLA_WARNING_MINUTES);
        List<Order> orders = rootView
            ? orderDAO.findSlaAttention(threshold)
            : orderDAO.findSlaAttentionForAdmin(actor.getUserId(), threshold);
        if (orders.isEmpty()) return;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                List<Long> escalationRecipients = rootView
                    ? List.of() : notificationDAO.findActiveRootIds(connection);
                for (Order order : orders) {
                    LocalDateTime missedDueAt = SlaDisplayUtil.breachedDueAt(order, now);
                    boolean breached = missedDueAt != null || "BREACHED".equals(order.getSlaState());
                    LocalDateTime dueAt = missedDueAt == null ? SlaDisplayUtil.nextDueAt(order) : missedDueAt;
                    if (dueAt == null || (!breached && dueAt.isAfter(threshold))) continue;
                    if (breached) orderDAO.markSlaBreached(connection, order.getOrderId(), now);
                    String dueKey = dueAt.truncatedTo(ChronoUnit.MINUTES).toString();
                    notify(connection, actor.getUserId(), order.getItemId(),
                        breached && rootView ? "SLA_ESCALATED" : breached ? "SLA_BREACHED" : "SLA_WARNING",
                        breached && rootView ? "工单 SLA 超时升级" : breached ? "工单 SLA 已超时" : "工单 SLA 即将到期",
                        "工单 #" + order.getItemId() + " " + SlaDisplayUtil.countdown(order),
                        (breached ? "sla-breach:" : "sla-warning:") + order.getOrderId() + ":" + dueKey);
                    if (breached && !rootView) {
                        for (Long rootId : escalationRecipients) {
                            notify(connection, rootId, order.getItemId(), "SLA_ESCALATED", "工单 SLA 超时升级",
                                "工单 #" + order.getItemId() + " 已超时，当前负责人 #" + actor.getUserId(),
                                "sla-breach:" + order.getOrderId() + ":" + dueKey);
                        }
                    }
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw new com.ticket.exception.BusinessException("刷新 SLA 通知失败", ex);
        }
    }

    public void escalateResolvedBreach(Connection connection, Order order) throws Exception {
        if (order == null || !"BREACHED".equals(order.getSlaState())) return;
        LocalDateTime missedDueAt = SlaDisplayUtil.breachedDueAt(order, LocalDateTime.now());
        LocalDateTime keyAt = missedDueAt == null ? order.getResolvedAt() : missedDueAt;
        if (keyAt == null) keyAt = LocalDateTime.now();
        String dueKey = keyAt.truncatedTo(ChronoUnit.MINUTES).toString();
        for (Long rootId : notificationDAO.findActiveRootIds(connection)) {
            notify(connection, rootId, order.getItemId(), "SLA_ESCALATED", "工单 SLA 超时升级",
                "工单 #" + order.getItemId() + " 在解决时确认 SLA 已超时",
                "sla-breach:" + order.getOrderId() + ":" + dueKey);
        }
    }
}
