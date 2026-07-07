package com.ticket.dto;

import java.math.BigDecimal;

public class RecommendationDTO {
    private Long itemId;
    private String title;
    private Long categoryId;
    private String categoryName;
    private BigDecimal score = BigDecimal.ZERO;
    private String reason;
    private long userHistoryCount;
    private long globalActionCount;
    private long ratingCount;
    private Double averageRating;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getUserHistoryCount() {
        return userHistoryCount;
    }

    public void setUserHistoryCount(long userHistoryCount) {
        this.userHistoryCount = userHistoryCount;
    }

    public long getGlobalActionCount() {
        return globalActionCount;
    }

    public void setGlobalActionCount(long globalActionCount) {
        this.globalActionCount = globalActionCount;
    }

    public long getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(long ratingCount) {
        this.ratingCount = ratingCount;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    @Override
    public String toString() {
        return title + " / " + categoryName + " / score=" + score + " / " + reason;
    }
}
