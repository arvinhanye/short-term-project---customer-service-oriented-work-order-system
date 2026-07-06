package com.ticket.dao.mongo;

import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.SystemLog;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

public class SystemLogDAO extends MongoBaseDAO {
    public void insert(SystemLog log) {
        collection("system_logs").insertOne(new Document("user_id", log.getUserId())
            .append("log_type", log.getLogType())
            .append("log_level", log.getLogLevel())
            .append("message", log.getMessage())
            .append("action_detail", new Document("ip", log.getActionDetail().getIp())
                .append("operation", log.getActionDetail().getOperation()))
            .append("timestamp", java.util.Date.from(log.getTimestamp())));
    }

    public List<Document> findRecent(int limit) {
        return collection("system_logs")
            .find()
            .sort(new Document("timestamp", -1))
            .limit(limit)
            .into(new ArrayList<>());
    }

    public List<Document> aggregateByLogType() {
        return collection("system_logs").aggregate(List.of(
            new Document("$group", new Document("_id", "$log_type").append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("count", -1))
        )).into(new ArrayList<>());
    }
}
