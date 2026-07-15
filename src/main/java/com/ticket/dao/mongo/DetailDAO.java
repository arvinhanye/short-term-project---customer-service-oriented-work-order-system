package com.ticket.dao.mongo;

import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;

public class DetailDAO extends MongoBaseDAO {
    private static final java.time.ZoneId BEIJING = java.time.ZoneId.of("Asia/Shanghai");
    public void upsert(ItemDetail detail) {
        collection("item_details").updateOne(
            new Document("item_id", detail.getItemId()),
            new Document("$set", toDocument(detail)),
            new com.mongodb.client.model.UpdateOptions().upsert(true)
        );
    }

    public ItemDetail findByItemId(String itemId) {
        Document document = collection("item_details").find(new Document("item_id", itemId)).first();
        return document == null ? null : fromDocument(document);
    }

    public Map<String, ItemDetail> findByItemIds(Collection<String> itemIds) {
        Map<String, ItemDetail> details = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return details;
        }
        for (Document document : collection("item_details")
            .find(new Document("item_id", new Document("$in", new ArrayList<>(itemIds))))) {
            ItemDetail detail = fromDocument(document);
            details.put(detail.getItemId(), detail);
        }
        return details;
    }

    public void deleteByItemId(String itemId) {
        collection("item_details").deleteOne(new Document("item_id", itemId));
    }

    /** MySQL 是工作流当前态的权威数据源，MongoDB 仅保留兼容镜像。 */
    public void syncWorkflow(Order order) {
        Document workflow = new Document("metadata.assigned_admin_id", stringValue(order.getAssignedAdminId()))
            .append("metadata.transfer_request_id", order.getTransferRequestId())
            .append("metadata.transfer_requested_by_admin_id", stringValue(order.getTransferRequestedBy()))
            .append("metadata.transfer_target_admin_id", stringValue(order.getTransferTargetAdminId()))
            .append("metadata.transfer_reason", order.getTransferReason())
            .append("metadata.transfer_requested_at", toDate(order.getTransferRequestedAt()))
            .append("metadata.reminder_count", order.getReminderCount())
            .append("metadata.last_reminded_at", toDate(order.getLastRemindedAt()));
        collection("item_details").updateOne(new Document("item_id", String.valueOf(order.getItemId())),
            new Document("$set", workflow));
    }

    private Document toDocument(ItemDetail detail) {
        return new Document("item_id", detail.getItemId())
            .append("description", detail.getDescription())
            .append("images", detail.getImages())
            .append("metadata", new Document("language", detail.getMetadata().getLanguage())
                .append("priority", detail.getMetadata().getPriority())
                .append("created_by_user_id", detail.getMetadata().getCreatedByUserId())
                .append("assigned_admin_id", detail.getMetadata().getAssignedAdminId())
                .append("transfer_request_id", detail.getMetadata().getTransferRequestId())
                .append("transfer_requested_by_admin_id", detail.getMetadata().getTransferRequestedByAdminId())
                .append("transfer_target_admin_id", detail.getMetadata().getTransferTargetAdminId())
                .append("transfer_reason", detail.getMetadata().getTransferReason())
                .append("transfer_requested_at", toDate(detail.getMetadata().getTransferRequestedAt()))
                .append("reminder_count", detail.getMetadata().getReminderCount())
                .append("last_reminded_at", toDate(detail.getMetadata().getLastRemindedAt()))
                .append("contact_channel", detail.getMetadata().getContactChannel())
                .append("last_processed_at", toDate(detail.getMetadata().getLastProcessedAt())));
    }

    private ItemDetail fromDocument(Document document) {
        ItemDetail detail = new ItemDetail();
        detail.setItemId(document.getString("item_id"));
        detail.setDescription(document.getString("description"));
        var images = document.getList("images", String.class);
        detail.setImages(images == null ? new ArrayList<>() : images);
        Document metadata = document.get("metadata", Document.class);
        if (metadata != null) {
            ItemDetail.Metadata itemMetadata = new ItemDetail.Metadata();
            itemMetadata.setLanguage(metadata.getString("language"));
            itemMetadata.setPriority(metadata.getString("priority"));
            itemMetadata.setCreatedByUserId(metadata.getString("created_by_user_id"));
            itemMetadata.setAssignedAdminId(metadata.getString("assigned_admin_id"));
            itemMetadata.setTransferRequestId(metadata.getString("transfer_request_id"));
            itemMetadata.setTransferRequestedByAdminId(metadata.getString("transfer_requested_by_admin_id"));
            itemMetadata.setTransferTargetAdminId(metadata.getString("transfer_target_admin_id"));
            itemMetadata.setTransferReason(metadata.getString("transfer_reason"));
            itemMetadata.setTransferRequestedAt(toInstant(metadata.getDate("transfer_requested_at")));
            Number reminderCount = metadata.get("reminder_count", Number.class);
            itemMetadata.setReminderCount(reminderCount == null ? 0 : reminderCount.intValue());
            itemMetadata.setLastRemindedAt(toInstant(metadata.getDate("last_reminded_at")));
            itemMetadata.setContactChannel(metadata.getString("contact_channel"));
            itemMetadata.setLastProcessedAt(toInstant(metadata.getDate("last_processed_at")));
            detail.setMetadata(itemMetadata);
        }
        return detail;
    }

    private java.util.Date toDate(Instant instant) {
        return instant == null ? null : java.util.Date.from(instant);
    }

    private Instant toInstant(java.util.Date date) {
        return date == null ? null : Instant.ofEpochMilli(date.getTime());
    }

    private java.util.Date toDate(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : java.util.Date.from(dateTime.atZone(BEIJING).toInstant());
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
