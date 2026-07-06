package com.ticket.dao.mongo;

import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.ActionLog;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

public class LogDAO extends MongoBaseDAO {
    public void insert(ActionLog log) {
        collection("action_logs").insertOne(new Document("user_id", log.getUserId())
            .append("item_id", log.getItemId())
            .append("action_type", log.getActionType())
            .append("duration_seconds", log.getDurationSeconds())
            .append("client_info", new Document("client_type", log.getClientInfo().getClientType())
                .append("ip", log.getClientInfo().getIp()))
            .append("created_at", java.util.Date.from(log.getCreatedAt())));
    }

    public List<Document> aggregateUserActions() {
        return collection("action_logs").aggregate(List.of(
            new Document("$group", new Document("_id", "$user_id").append("action_count", new Document("$sum", 1))),
            new Document("$sort", new Document("action_count", -1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateHotItems() {
        return collection("action_logs").aggregate(List.of(
            new Document("$group", new Document("_id", "$item_id").append("view_count", new Document("$sum", 1))),
            new Document("$sort", new Document("view_count", -1)),
            new Document("$limit", 10)
        )).into(new ArrayList<>());
    }
}
