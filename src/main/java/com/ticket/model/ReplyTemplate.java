package com.ticket.model;

public class ReplyTemplate {
    private Long templateId;
    private String templateName;
    private String content;
    private Long categoryId;
    private boolean enabled;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long value) { templateId = value; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String value) { templateName = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    @Override public String toString() { return templateName; }
}
