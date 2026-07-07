package com.ticket.dao.mongo;

import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.Comment;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;

public class CommentDAO extends MongoBaseDAO {
    public void insert(Comment comment) {
        collection("comments").insertOne(toDocument(comment));
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
            new Document("$match", new Document("tags", "CUSTOMER_RATING")),
            ratingProjection(),
            new Document("$group", new Document("_id", "$item_id")
                .append("avg_rating", new Document("$avg", "$rating_value"))
                .append("rating_count", new Document("$sum", 1))
                .append("last_rating_at", new Document("$max", "$created_at"))),
            new Document("$sort", new Document("avg_rating", -1).append("rating_count", -1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateTagSummary() {
        return collection("comments").aggregate(List.of(
            new Document("$unwind", "$tags"),
            new Document("$group", new Document("_id", "$tags")
                .append("comment_count", new Document("$sum", 1))
                .append("user_count", new Document("$addToSet", "$user_id"))
                .append("last_comment_at", new Document("$max", "$created_at"))),
            new Document("$project", new Document("comment_count", 1)
                .append("last_comment_at", 1)
                .append("unique_user_count", new Document("$size", "$user_count"))),
            new Document("$sort", new Document("comment_count", -1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateRatingDistribution() {
        return collection("comments").aggregate(List.of(
            new Document("$match", new Document("tags", "CUSTOMER_RATING")),
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
            .append("item_id", comment.getItemId())
            .append("content", comment.getContent())
            .append("rating", comment.getRating() == null ? "" : comment.getRating())
            .append("tags", comment.getTags() == null ? new ArrayList<>() : comment.getTags())
            .append("created_at", Date.from(createdAt));
    }

    private Comment fromDocument(Document document) {
        Comment comment = new Comment();
        comment.setUserId(document.getString("user_id"));
        comment.setItemId(document.getString("item_id"));
        comment.setContent(document.getString("content"));
        comment.setRating(document.getString("rating"));
        var tags = document.getList("tags", String.class);
        comment.setTags(tags == null ? new ArrayList<>() : tags);
        comment.setCreatedAt(Instant.ofEpochMilli(document.getDate("created_at").getTime()));
        return comment;
    }

    private Document ratingProjection() {
        return new Document("$addFields", new Document("rating_value", new Document("$convert",
            new Document("input", "$rating")
                .append("to", "int")
                .append("onError", 0)
                .append("onNull", 0))));
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
