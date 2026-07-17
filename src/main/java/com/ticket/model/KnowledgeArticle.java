package com.ticket.model;

import java.time.LocalDateTime;

public class KnowledgeArticle {
    private Long articleId;
    private String title;
    private String summary;
    private String content;
    private Long categoryId;
    private String keywords;
    private String status;
    private Long createdBy;
    private LocalDateTime updatedAt;

    public Long getArticleId() { return articleId; }
    public void setArticleId(Long value) { articleId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { summary = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId = value; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String value) { keywords = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    @Override public String toString() { return title; }
}
