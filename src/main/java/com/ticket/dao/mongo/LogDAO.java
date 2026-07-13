package com.ticket.dao.mongo;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.ActionLog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.bson.Document;

public class LogDAO extends MongoBaseDAO {
    private static final int DEFAULT_ANALYTICS_DAYS = 30;
    private static final int DEFAULT_TOP_N = 20;
    public void insert(ActionLog log) {
        ActionLog.ClientInfo clientInfo = log.getClientInfo() == null ? new ActionLog.ClientInfo() : log.getClientInfo();
        Instant createdAt = log.getCreatedAt() == null ? Instant.now() : log.getCreatedAt();
        collection("action_logs").insertOne(new Document("user_id", log.getUserId())
            .append("item_id", log.getItemId())
            .append("action_type", log.getActionType())
            .append("duration_seconds", log.getDurationSeconds() == null ? "0" : log.getDurationSeconds())
            .append("client_info", new Document("client_type", clientInfo.getClientType())
                .append("ip", clientInfo.getIp()))
            .append("created_at", Date.from(createdAt)));
    }

    public List<Document> findRecent(int limit) {
        return collection("action_logs")
            .find()
            .sort(Sorts.descending("created_at"))
            .limit(normalizeLimit(limit))
            .into(new ArrayList<>());
    }

    public List<Document> findRecentByUser(String userId, int limit) {
        return collection("action_logs")
            .find(Filters.eq("user_id", userId))
            .sort(Sorts.descending("created_at"))
            .limit(normalizeLimit(limit))
            .into(new ArrayList<>());
    }

    public List<Document> findByItemId(String itemId, int limit) {
        return collection("action_logs")
            .find(Filters.eq("item_id", itemId))
            .sort(Sorts.descending("created_at"))
            .limit(normalizeLimit(limit))
            .into(new ArrayList<>());
    }

    public List<Document> aggregateUserActions() {
        return collection("action_logs").aggregate(List.of(
            recentMatch(),
            durationProjection(),
            new Document("$group", new Document("_id", "$user_id")
                .append("action_count", new Document("$sum", 1))
                .append("action_types", new Document("$addToSet", "$action_type"))
                .append("total_duration_seconds", new Document("$sum", "$duration_value"))
                .append("avg_duration_seconds", new Document("$avg", "$duration_value"))
                .append("last_action_at", new Document("$max", "$created_at"))),
            new Document("$sort", new Document("action_count", -1)),
            new Document("$limit", DEFAULT_TOP_N)
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateHotItems() {
        return collection("action_logs").aggregate(List.of(
            new Document("$match", new Document("item_id", new Document("$nin", Arrays.asList(null, "")))
                .append("created_at", new Document("$gte", Date.from(recentSince())))),
            new Document("$group", new Document("_id", "$item_id")
                .append("action_count", new Document("$sum", 1))
                .append("view_count", new Document("$sum", new Document("$cond", List.of(new Document("$eq", List.of("$action_type", "VIEW")), 1, 0))))
                .append("last_action_at", new Document("$max", "$created_at"))
                .append("action_types", new Document("$addToSet", "$action_type"))),
            new Document("$sort", new Document("action_count", -1).append("last_action_at", -1)),
            new Document("$limit", 10)
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateActionTypeSummary() {
        return collection("action_logs").aggregate(List.of(
            recentMatch(),
            durationProjection(),
            new Document("$group", new Document("_id", "$action_type")
                .append("action_count", new Document("$sum", 1))
                .append("user_count", new Document("$addToSet", "$user_id"))
                .append("avg_duration_seconds", new Document("$avg", "$duration_value"))
                .append("last_action_at", new Document("$max", "$created_at"))),
            new Document("$project", new Document("action_count", 1)
                .append("avg_duration_seconds", 1)
                .append("last_action_at", 1)
                .append("unique_user_count", new Document("$size", "$user_count"))),
            new Document("$sort", new Document("action_count", -1)),
            new Document("$limit", DEFAULT_TOP_N)
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateDailyActions(int days) {
        Instant since = Instant.now().minusSeconds((long) Math.max(1, days) * 24 * 60 * 60);
        return collection("action_logs").aggregate(List.of(
            new Document("$match", new Document("created_at", new Document("$gte", Date.from(since)))),
            new Document("$group", new Document("_id", new Document("day", dateToDay("$created_at"))
                    .append("action_type", "$action_type"))
                .append("action_count", new Document("$sum", 1))),
            new Document("$sort", new Document("_id.day", 1).append("_id.action_type", 1))
        )).into(new ArrayList<>());
    }

    public List<Document> aggregateClientUsage() {
        return collection("action_logs").aggregate(List.of(
            recentMatch(),
            new Document("$group", new Document("_id", "$client_info.client_type")
                .append("action_count", new Document("$sum", 1))
                .append("user_count", new Document("$addToSet", "$user_id"))
                .append("last_action_at", new Document("$max", "$created_at"))),
            new Document("$project", new Document("action_count", 1)
                .append("last_action_at", 1)
                .append("unique_user_count", new Document("$size", "$user_count"))),
            new Document("$sort", new Document("action_count", -1)),
            new Document("$limit", DEFAULT_TOP_N)
        )).into(new ArrayList<>());
    }

    private Document durationProjection() {
        return new Document("$addFields", new Document("duration_value", new Document("$convert",
            new Document("input", "$duration_seconds")
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
