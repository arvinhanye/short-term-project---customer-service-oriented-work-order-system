package com.ticket.model;

public class AssignmentRule {
    private Long ruleId;
    private String ruleName;
    private Long categoryId;
    private String categoryName;
    private String priority;
    private String strategy;
    private Long targetAdminId;
    private String targetAdminName;
    private boolean enabled;
    private int sortOrder;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long value) { ruleId = value; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String value) { ruleName = value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId = value; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String value) { categoryName = value; }
    public String getPriority() { return priority; }
    public void setPriority(String value) { priority = value; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String value) { strategy = value; }
    public Long getTargetAdminId() { return targetAdminId; }
    public void setTargetAdminId(Long value) { targetAdminId = value; }
    public String getTargetAdminName() { return targetAdminName; }
    public void setTargetAdminName(String value) { targetAdminName = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { sortOrder = value; }
}
