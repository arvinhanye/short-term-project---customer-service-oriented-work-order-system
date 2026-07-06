package com.ticket.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ItemDetail {
    private String itemId;
    private String description;
    private List<String> images = new ArrayList<>();
    private Metadata metadata = new Metadata();

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public static class Metadata {
        private String language;
        private String priority;
        private String createdByUserId;
        private String assignedAdminId;
        private String contactChannel;
        private Instant lastProcessedAt;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getCreatedByUserId() {
            return createdByUserId;
        }

        public void setCreatedByUserId(String createdByUserId) {
            this.createdByUserId = createdByUserId;
        }

        public String getAssignedAdminId() {
            return assignedAdminId;
        }

        public void setAssignedAdminId(String assignedAdminId) {
            this.assignedAdminId = assignedAdminId;
        }

        public String getContactChannel() {
            return contactChannel;
        }

        public void setContactChannel(String contactChannel) {
            this.contactChannel = contactChannel;
        }

        public Instant getLastProcessedAt() {
            return lastProcessedAt;
        }

        public void setLastProcessedAt(Instant lastProcessedAt) {
            this.lastProcessedAt = lastProcessedAt;
        }
    }
}
