package com.ticket.dao.mongo;

import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.ItemDetail;
import java.time.Instant;
import java.util.ArrayList;
import org.bson.Document;

public class DetailDAO extends MongoBaseDAO {
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

    public void deleteByItemId(String itemId) {
        collection("item_details").deleteOne(new Document("item_id", itemId));
    }

    private Document toDocument(ItemDetail detail) {
        return new Document("item_id", detail.getItemId())
            .append("description", detail.getDescription())
            .append("images", detail.getImages())
            .append("metadata", new Document("language", detail.getMetadata().getLanguage())
                .append("priority", detail.getMetadata().getPriority())
                .append("created_by_user_id", detail.getMetadata().getCreatedByUserId())
                .append("assigned_admin_id", detail.getMetadata().getAssignedAdminId())
                .append("contact_channel", detail.getMetadata().getContactChannel())
                .append("last_processed_at", detail.getMetadata().getLastProcessedAt() == null ? null : java.util.Date.from(detail.getMetadata().getLastProcessedAt())));
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
            itemMetadata.setContactChannel(metadata.getString("contact_channel"));
            java.util.Date lastProcessedAt = metadata.getDate("last_processed_at");
            itemMetadata.setLastProcessedAt(lastProcessedAt == null ? null : Instant.ofEpochMilli(lastProcessedAt.getTime()));
            detail.setMetadata(itemMetadata);
        }
        return detail;
    }
}
