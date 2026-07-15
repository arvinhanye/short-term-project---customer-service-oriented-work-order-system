package com.ticket.service;

import com.ticket.exception.BusinessException;
import com.ticket.dao.mongo.DetailDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.model.Order;
import com.ticket.model.TicketHistory;
import com.ticket.model.User;
import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

public class MaintenanceService {
    private final AuditLogService auditLogService = new AuditLogService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final DetailDAO detailDAO = new DetailDAO();
    private final TicketHistoryService historyService = new TicketHistoryService();

    public int batchUpdateOrderStatus(User actor, int oldStatus, int newStatus, LocalDateTime beforeTime) {
        UserService.requireBusinessAdmin(actor);
        if (beforeTime == null || beforeTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException("批处理截止时间不合法");
        }
        BusinessService.validateStatusTransition(oldStatus, newStatus);
        List<Order> changed;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                changed = orderDAO.findForBatchUpdate(connection, oldStatus, beforeTime, actor.getUserId());
                for (Order order : changed) {
                    if (orderDAO.updateStatusForMaintenance(connection, order, newStatus) != 1) {
                        throw new BusinessException("工单状态已被其他操作修改，请重试");
                    }
                    String eventType = newStatus == 4 ? "AUTO_CANCELLED" : "BATCH_STATUS_CHANGED";
                    TicketHistory history = historyService.event(order, actor, eventType,
                        TicketHistoryService.PUBLIC, order.getWorkflowVersion() + 1);
                    history.setFromStatus(oldStatus);
                    history.setToStatus(newStatus);
                    history.setFromAdminId(order.getAssignedAdminId());
                    history.setToAdminId(order.getAssignedAdminId());
                    history.setReason("系统批处理：截止 " + beforeTime);
                    history.setSourceType("MAINTENANCE_BATCH");
                    history.setSourceId("before:" + beforeTime);
                    historyService.append(connection, history);
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }

            // MySQL 已提交后再同步兼容镜像；镜像失败不回滚权威工作流与历史。
            for (Order order : changed) {
                order.setStatus(newStatus);
                order.setWorkflowVersion(order.getWorkflowVersion() + 1);
                order.setTransferRequestId(null);
                order.setTransferRequestedBy(null);
                order.setTransferTargetAdminId(null);
                order.setTransferReason(null);
                order.setTransferRequestedAt(null);
                try {
                    detailDAO.syncWorkflow(order);
                } catch (Exception mirrorEx) {
                    auditLogService.write(String.valueOf(actor.getUserId()), "DB_ERROR", "WARN",
                        "工单 " + order.getItemId() + " 的 MongoDB 工作流镜像同步失败", "SYNC_WORKFLOW_MIRROR");
                }
            }
            auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO",
                "批量更新工单状态，影响 " + changed.size() + " 行", "BATCH_UPDATE_STATUS");
            return changed.size();
        } catch (Exception ex) {
            auditLogService.write(String.valueOf(actor.getUserId()), "DB_ERROR", "ERROR",
                "批量更新工单状态失败", "BATCH_UPDATE_STATUS");
            throw new BusinessException("批量更新工单状态失败", ex);
        }
    }
}
