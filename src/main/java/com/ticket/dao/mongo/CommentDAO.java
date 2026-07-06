package com.ticket.dao.mongo;

import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.Comment;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.util.ArrayList;
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

    public List<Document> aggregateAverageRatingByItem() {
        return collection("comments").aggregate(List.of(
            new Document("$match", new Document("tags", "CUSTOMER_RATING")),
            new Document("$group", new Document("_id", "$item_id").append("avg_rating", new Document("$avg", new Document("$toInt", "$rating"))))
        )).into(new ArrayList<>());
    }

    private Document toDocument(Comment comment) {
        return new Document("user_id", comment.getUserId())
            .append("item_id", comment.getItemId())
            .append("content", comment.getContent())
            .append("rating", comment.getRating())
            .append("tags", comment.getTags())
            .append("created_at", java.util.Date.from(comment.getCreatedAt()));
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
}
