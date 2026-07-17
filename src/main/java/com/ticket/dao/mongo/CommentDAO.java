package com.ticket.dao.mongo;

import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.Comment;
import com.ticket.model.TicketAttachment;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import org.bson.Document;

public class CommentDAO extends MongoBaseDAO {
    private static final int DEFAULT_ANALYTICS_DAYS = 30;
    private static final int DEFAULT_TOP_N = 20;
    public void insert(Comment comment) {
        collection("comments").insertOne(toDocument(comment));
    }

    public void deleteByEventId(String eventId) {
        collection("comments").deleteOne(Filters.eq("event_id", eventId));
    }

    public boolean hasRating(String itemId, String userId) {
        return collection("comments").countDocuments(Filters.and(
            Filters.eq("item_id", itemId), Filters.eq("user_id", userId),
            Filters.eq("tags", "CUSTOMER_RATING"))) > 0;
    }

    public Set<String> findReferencedAttachmentIds() {
        Set<String> result = new LinkedHashSet<>();
        for (Document comment : collection("comments").find(Filters.exists("attachments.0"))
                .projection(new Document("attachments.file_id", 1))) {
            Object attachments = comment.get("attachments");
            if (!(attachments instanceof List<?> values)) continue;
            for (Object value : values) {
                if (value instanceof Document attachment) {
                    String fileId = attachment.getString("file_id");
                    if (fileId != null && !fileId.isBlank()) result.add(fileId);
                }
            }
        }
        return result;
    }

    public boolean containsVisibleAttachment(String itemId, String fileId, boolean includeInternal) {
        var attachmentFilter = Filters.and(
            Filters.eq("item_id", itemId),
            Filters.elemMatch("attachments", Filters.eq("file_id", fileId)));
        var filter = includeInternal ? attachmentFilter
            : Filters.and(attachmentFilter, Filters.ne("tags", "INTERNAL_NOTE"));
        return collection("comments").countDocuments(filter) > 0;
    }

    public List<Comment> findByItemId(String itemId, boolean includeInternal) {
        var filter = includeInternal
            ? Filters.eq("item_id", itemId)
            : Filters.and(Filters.eq("item_id", itemId), Filters.ne("tags", "INTERNAL_NOTE"));
        List<Comment> comments = new ArrayList<>();
        for (Document document : collection("comments").find(filter).sort(Sorts.ascending("created_at"))) {
            comments.add(fromDocument(document));
        }
        return comments;
    }

    public List<Comment> findRecent(int limit, boolean includeInternal) {
        var filter = includeInternal ? new Document() : Filters.ne("tags", "INTERNAL_NOTE");
        List<Comment> comments = new ArrayList<>();
        for (Document document : collection("comments")
            .find(filter)
            .sort(Sorts.descending("created_at"))
            .limit(normalizeLimit(limit))) {
            comments.add(fromDocument(document));
        }
        return comments;
    }

    public List<Comment> findByUserId(String userId, int limit, boolean includeInternal) {
        var filter = includeInternal
            ? Filters.eq("user_id", userId)
            : Filters.and(Filters.eq("user_id", userId), Filters.ne("tags", "INTERNAL_NOTE"));
        List<Comment> comments = new ArrayList<>();
        for (Document document : collection("comments")
            .find(filter)
            .sort(Sorts.descending("created_at"))
            .limit(normalizeLimit(limit))) {
            comments.add(fromDocument(document));
        }
        return comments;
    }

    public List<Document> aggregateAverageRatingByItem() {
        return collection("comments").aggregate(List.of(
            new Document("$match", new Document("tags", "CUSTOMER_RATING")
                .append("created_at", new Document("$gte", Date.from(recentSince())))),
            ratingProjection(),
            new Document("$group", new Document("_id", "$item_id")
                .append("avg_rating", new Document("$avg", "$rating_value"))
                .append("rating_count", new Document("$sum", 1))
                .append("last_rating_at", new Document("$max", "$created_at"))),
            new Document("$sort", new Document("avg_rating", -1).append("rating_count", -1)),
            new Document("$limit", DEFAULT_TOP_N)
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateTagSummary() {
        return collection("comments").aggregate(List.of(
            recentMatch(),
            new Document("$unwind", "$tags"),
            new Document("$group", new Document("_id", "$tags")
                .append("comment_count", new Document("$sum", 1))
                .append("user_count", new Document("$addToSet", "$user_id"))
                .append("last_comment_at", new Document("$max", "$created_at"))),
            new Document("$project", new Document("comment_count", 1)
                .append("last_comment_at", 1)
                .append("unique_user_count", new Document("$size", "$user_count"))),
            new Document("$sort", new Document("comment_count", -1)),
            new Document("$limit", DEFAULT_TOP_N)
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateRatingDistribution() {
        return collection("comments").aggregate(List.of(
            new Document("$match", new Document("tags", "CUSTOMER_RATING")
                .append("created_at", new Document("$gte", Date.from(recentSince())))),
            ratingProjection(),
            new Document("$group", new Document("_id", "$rating_value")
                .append("rating_count", new Document("$sum", 1))),
            new Document("$sort", new Document("_id", 1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateCommentTrend(int days) {
        Instant since = Instant.now().minusSeconds((long) Math.max(1, days) * 24 * 60 * 60);
        return collection("comments").aggregate(List.of(
            new Document("$match", new Document("created_at", new Document("$gte", Date.from(since)))),
            new Document("$unwind", "$tags"),
            new Document("$group", new Document("_id", new Document("day", dateToDay("$created_at"))
                    .append("tag", "$tags"))
                .append("comment_count", new Document("$sum", 1))),
            new Document("$sort", new Document("_id.day", 1).append("_id.tag", 1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateItemCommentStats() {
        return collection("comments").aggregate(List.of(
            recentMatch(),
            new Document("$group", new Document("_id", "$item_id")
                .append("comment_count", new Document("$sum", 1))
                .append("internal_note_count", new Document("$sum", new Document("$cond", List.of(
                    new Document("$in", List.of("INTERNAL_NOTE", "$tags")), 1, 0))))
                .append("rating_count", new Document("$sum", new Document("$cond", List.of(
                    new Document("$in", List.of("CUSTOMER_RATING", "$tags")), 1, 0))))
                .append("last_comment_at", new Document("$max", "$created_at"))),
            new Document("$sort", new Document("comment_count", -1).append("last_comment_at", -1)),
            new Document("$limit", 20)
        )).into(new ArrayList<>());
    }

    private Document toDocument(Comment comment) {
        Instant createdAt = comment.getCreatedAt() == null ? Instant.now() : comment.getCreatedAt();
        return new Document("user_id", comment.getUserId())
            .append("event_id", comment.getEventId())
            .append("item_id", comment.getItemId())
            .append("content", comment.getContent())
            .append("rating", comment.getRating() == null ? "" : comment.getRating())
            .append("tags", comment.getTags() == null ? new ArrayList<>() : comment.getTags())
            .append("attachments", toAttachmentDocuments(comment.getAttachments()))
            .append("sticker_code", comment.getStickerCode() == null ? "" : comment.getStickerCode())
            .append("created_at", Date.from(createdAt));
    }

    private Comment fromDocument(Document document) {
        Comment comment = new Comment();
        comment.setEventId(document.getString("event_id"));
        comment.setUserId(document.getString("user_id"));
        comment.setItemId(document.getString("item_id"));
        comment.setContent(document.getString("content"));
        comment.setRating(document.getString("rating"));
        var tags = document.getList("tags", String.class);
        comment.setTags(tags == null ? new ArrayList<>() : tags);
        comment.setAttachments(fromAttachmentDocuments(document.getList("attachments", Document.class)));
        comment.setStickerCode(document.getString("sticker_code"));
        Date createdAt = document.getDate("created_at");
        comment.setCreatedAt(createdAt == null ? Instant.EPOCH : Instant.ofEpochMilli(createdAt.getTime()));
        return comment;
    }

    private List<Document> toAttachmentDocuments(List<TicketAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }
        List<Document> documents = new ArrayList<>();
        for (TicketAttachment attachment : attachments) {
            documents.add(new Document("file_id", attachment.getFileId())
                .append("file_name", attachment.getFileName())
                .append("content_type", attachment.getContentType())
                .append("size", attachment.getSize())
                .append("image", attachment.isImage()));
        }
        return documents;
    }

    private List<TicketAttachment> fromAttachmentDocuments(List<Document> documents) {
        List<TicketAttachment> attachments = new ArrayList<>();
        if (documents == null) {
            return attachments;
        }
        for (Document document : documents) {
            TicketAttachment attachment = new TicketAttachment();
            attachment.setFileId(document.getString("file_id"));
            attachment.setFileName(document.getString("file_name"));
            attachment.setContentType(document.getString("content_type"));
            Number size = document.get("size", Number.class);
            attachment.setSize(size == null ? 0L : size.longValue());
            attachment.setImage(Boolean.TRUE.equals(document.getBoolean("image")));
            attachments.add(attachment);
        }
        return attachments;
    }

    private Document ratingProjection() {
        return new Document("$addFields", new Document("rating_value", new Document("$convert",
            new Document("input", "$rating")
                .append("to", "int")
                .append("onError", 0)
                .append("onNull", 0))));
    }

    private Document recentMatch() {
        return new Document("$match", new Document("created_at", new Document("$gte", Date.from(recentSince()))));
    }

    private Instant recentSince() {
        return Instant.now().minusSeconds((long) DEFAULT_ANALYTICS_DAYS * 24 * 60 * 60);
    }

    private Document dateToDay(String field) {
        return new Document("$dateToString", new Document("format", "%Y-%m-%d")
            .append("date", field)
            .append("timezone", "Asia/Shanghai"));
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
