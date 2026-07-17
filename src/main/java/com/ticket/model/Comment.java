package com.ticket.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Comment {
    private String eventId;
    private String userId;
    private String itemId;
    private String content;
    private String rating;
    private List<String> tags = new ArrayList<>();
    private List<TicketAttachment> attachments = new ArrayList<>();
    private String stickerCode;
    private Instant createdAt;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<TicketAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<TicketAttachment> attachments) {
        this.attachments = attachments;
    }

    public String getStickerCode() {
        return stickerCode;
    }

    public void setStickerCode(String stickerCode) {
        this.stickerCode = stickerCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
