package com.ticket.service;

import com.ticket.dao.mongo.DetailDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import com.ticket.model.TicketHistory;
import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.bson.Document;

public class TicketHistoryMigrationService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private final DetailDAO detailDAO = new DetailDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final TicketHistoryService historyService = new TicketHistoryService();

    /** 可重复执行：只回填 workflow_version=0 的旧工单。 */
    public int migrateExistingWorkflow() {
        int migrated = 0;
        while (true) {
            var itemIds = orderDAO.findUnmigratedItemIds(500);
            if (itemIds.isEmpty()) break;
            int batchMigrated = 0;
            for (Long itemId : itemIds) {
                ItemDetail detail = detailDAO.findByItemId(String.valueOf(itemId));
                try (Connection connection = MySQLDBUtil.getWriteConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        Order order = orderDAO.findByItemIdForUpdate(connection, itemId);
                        if (order == null || order.getWorkflowVersion() != 0) {
                            connection.rollback();
                            continue;
                        }
                        ItemDetail.Metadata metadata = detail == null ? null : detail.getMetadata();
                        Long assigned = metadata == null ? null : parseLong(metadata.getAssignedAdminId());
                        Long requester = metadata == null ? null : parseLong(metadata.getTransferRequestedByAdminId());
                        Long target = metadata == null ? null : parseLong(metadata.getTransferTargetAdminId());
                        String requestId = target == null ? null : UUID.randomUUID().toString();
                        int reminderCount = metadata == null ? 0 : metadata.getReminderCount();
                        if (orderDAO.migrateWorkflowSnapshot(connection, order, assigned, requestId, requester, target,
                                metadata == null ? null : metadata.getTransferReason(),
                                metadata == null ? null : local(metadata.getTransferRequestedAt()), reminderCount,
                                metadata == null ? null : local(metadata.getLastRemindedAt())) != 1) {
                            connection.rollback();
                            continue;
                        }
                        TicketHistory history = historyService.event(order, null, "MIGRATION_SNAPSHOT",
                            TicketHistoryService.PUBLIC, 1);
                        history.setToStatus(order.getStatus());
                        history.setToAdminId(assigned);
                        history.setSourceType("LEGACY_MIGRATION");
                        history.setSourceId("item:" + itemId);
                        history.setEventPayload(new Document("history_before_migration_complete", false)
                            .append("reminder_count", reminderCount).toJson());
                        history.setOccurredAt(order.getCreatedAt());
                        historyService.append(connection, history);
                        connection.commit();
                        migrated++;
                        batchMigrated++;
                    } catch (Exception ex) {
                        connection.rollback();
                        throw ex;
                    } finally {
                        connection.setAutoCommit(true);
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException("回填工单历史失败，itemId=" + itemId, ex);
                }
            }
            if (batchMigrated == 0) break;
        }
        return migrated;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, BEIJING);
    }
}
