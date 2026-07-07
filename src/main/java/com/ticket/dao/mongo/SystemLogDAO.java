package com.ticket.dao.mongo;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.SystemLog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.conversions.Bson;
import org.bson.Document;

public class SystemLogDAO extends MongoBaseDAO {
    public void insert(SystemLog log) {
        SystemLog.ActionDetail actionDetail = log.getActionDetail() == null ? new SystemLog.ActionDetail() : log.getActionDetail();
        Instant timestamp = log.getTimestamp() == null ? Instant.now() : log.getTimestamp();
        collection("system_logs").insertOne(new Document("user_id", log.getUserId())
            .append("log_type", log.getLogType())
            .append("log_level", log.getLogLevel())
            .append("message", log.getMessage())
            .append("action_detail", new Document("ip", actionDetail.getIp())
                .append("operation", actionDetail.getOperation()))
            .append("timestamp", Date.from(timestamp)));
    }

    public List<Document> findRecent(int limit) {
        return collection("system_logs")
            .find()
            .sort(Sorts.descending("timestamp"))
            .limit(normalizeLimit(limit))
            .into(new ArrayList<>());
    }

    public List<Document> findByCondition(String logType, String logLevel, String userId, String keyword, int limit) {
        List<Bson> filters = new ArrayList<>();
        if (logType != null && !logType.isBlank()) {
            filters.add(Filters.eq("log_type", logType.trim()));
        }
        if (logLevel != null && !logLevel.isBlank()) {
            filters.add(Filters.eq("log_level", logLevel.trim()));
        }
        if (userId != null && !userId.isBlank()) {
            filters.add(Filters.eq("user_id", userId.trim()));
        }
        if (keyword != null && !keyword.isBlank()) {
            Pattern pattern = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
            filters.add(Filters.or(
                Filters.regex("message", pattern),
                Filters.regex("action_detail.operation", pattern)
            ));
        }
        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
        return collection("system_logs")
            .find(filter)
            .sort(Sorts.descending("timestamp"))
            .limit(normalizeLimit(limit))
            .into(new ArrayList<>());
    }

    public List<Document> aggregateByLogType() {
        return collection("system_logs").aggregate(List.of(
            new Document("$group", new Document("_id", "$log_type").append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("count", -1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateByLogLevel() {
        return collection("system_logs").aggregate(List.of(
            new Document("$group", new Document("_id", "$log_level")
                .append("count", new Document("$sum", 1))
                .append("last_seen_at", new Document("$max", "$timestamp"))),
            new Document("$sort", new Document("count", -1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateByUser(int limit) {
        return collection("system_logs").aggregate(List.of(
            new Document("$group", new Document("_id", "$user_id")
                .append("count", new Document("$sum", 1))
                .append("levels", new Document("$addToSet", "$log_level"))
                .append("types", new Document("$addToSet", "$log_type"))
                .append("last_seen_at", new Document("$max", "$timestamp"))),
            new Document("$sort", new Document("count", -1).append("last_seen_at", -1)),
            new Document("$limit", normalizeLimit(limit))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateDailyTrend(int days) {
        Instant since = Instant.now().minusSeconds((long) Math.max(1, days) * 24 * 60 * 60);
        return collection("system_logs").aggregate(List.of(
            new Document("$match", new Document("timestamp", new Document("$gte", Date.from(since)))),
            new Document("$group", new Document("_id", new Document("day", dateToDay("$timestamp"))
                    .append("level", "$log_level"))
                .append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("_id.day", 1).append("_id.level", 1))
        )).into(new ArrayList<>());
    }

    private Document dateToDay(String field) {
        return new Document("$dateToString", new Document("format", "%Y-%m-%d")
            .append("date", field)
            .append("timezone", "Asia/Shanghai"));
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }
}
