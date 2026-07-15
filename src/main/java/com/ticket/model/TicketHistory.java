package com.ticket.model;

import java.time.LocalDateTime;

public class TicketHistory {
    private Long historyId;
    private String eventId;
    private Long itemId;
    private Long orderId;
    private long eventSeq;
    private String eventType;
    private String visibility;
    private Long actorUserId;
    private String actorUsername;
    private String actorRole;
    private Long targetUserId;
    private Integer fromStatus;
    private Integer toStatus;
    private Long fromAdminId;
    private Long toAdminId;
    private String reason;
    private String sourceType;
    private String sourceId;
    private String eventPayload;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;

    public Long getHistoryId() { return historyId; }
    public void setHistoryId(Long historyId) { this.historyId = historyId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public long getEventSeq() { return eventSeq; }
    public void setEventSeq(long eventSeq) { this.eventSeq = eventSeq; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public Integer getFromStatus() { return fromStatus; }
    public void setFromStatus(Integer fromStatus) { this.fromStatus = fromStatus; }
    public Integer getToStatus() { return toStatus; }
    public void setToStatus(Integer toStatus) { this.toStatus = toStatus; }
    public Long getFromAdminId() { return fromAdminId; }
    public void setFromAdminId(Long fromAdminId) { this.fromAdminId = fromAdminId; }
    public Long getToAdminId() { return toAdminId; }
    public void setToAdminId(Long toAdminId) { this.toAdminId = toAdminId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getEventPayload() { return eventPayload; }
    public void setEventPayload(String eventPayload) { this.eventPayload = eventPayload; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
