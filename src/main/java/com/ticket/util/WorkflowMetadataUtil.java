package com.ticket.util;

import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import java.time.ZoneId;

public final class WorkflowMetadataUtil {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private WorkflowMetadataUtil() {
    }

    public static void apply(Order order, ItemDetail detail) {
        if (order == null || detail == null) {
            return;
        }
        ItemDetail.Metadata metadata = detail.getMetadata();
        if (metadata == null) {
            metadata = new ItemDetail.Metadata();
            detail.setMetadata(metadata);
        }
        metadata.setAssignedAdminId(stringValue(order.getAssignedAdminId()));
        metadata.setTransferRequestId(order.getTransferRequestId());
        metadata.setTransferRequestedByAdminId(stringValue(order.getTransferRequestedBy()));
        metadata.setTransferTargetAdminId(stringValue(order.getTransferTargetAdminId()));
        metadata.setTransferReason(order.getTransferReason());
        metadata.setTransferRequestedAt(order.getTransferRequestedAt() == null ? null
            : order.getTransferRequestedAt().atZone(BEIJING).toInstant());
        metadata.setReminderCount(order.getReminderCount());
        metadata.setLastRemindedAt(order.getLastRemindedAt() == null ? null
            : order.getLastRemindedAt().atZone(BEIJING).toInstant());
    }

    private static String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
