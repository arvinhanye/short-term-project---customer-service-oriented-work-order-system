package com.ticket.model;

import java.time.LocalDateTime;

public class Notification {
    private Long notificationId;
    private Long userId;
    private Long itemId;
    private String notificationType;
    private String title;
    private String content;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long value) { notificationId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String value) { notificationType = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime value) { readAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
