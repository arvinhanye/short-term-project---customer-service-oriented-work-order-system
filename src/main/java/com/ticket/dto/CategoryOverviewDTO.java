package com.ticket.dto;

public class CategoryOverviewDTO {
    private Long categoryId;
    private String name;
    private Long parentId;
    private String parentName;
    private Long parentParentId;
    private int childCount;
    private int directTicketCount;
    private int totalTicketCount;
    private boolean duplicateName;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
    public Long getParentParentId() { return parentParentId; }
    public void setParentParentId(Long parentParentId) { this.parentParentId = parentParentId; }
    public int getChildCount() { return childCount; }
    public void setChildCount(int childCount) { this.childCount = childCount; }
    public int getDirectTicketCount() { return directTicketCount; }
    public void setDirectTicketCount(int directTicketCount) { this.directTicketCount = directTicketCount; }
    public int getTotalTicketCount() { return totalTicketCount; }
    public void setTotalTicketCount(int totalTicketCount) { this.totalTicketCount = totalTicketCount; }
    public boolean isDuplicateName() { return duplicateName; }
    public void setDuplicateName(boolean duplicateName) { this.duplicateName = duplicateName; }

    public int getLevel() {
        if (parentId == null) return 1;
        return parentParentId == null ? 2 : 3;
    }

    public boolean isHierarchyAnomaly() {
        return getLevel() > 2;
    }

    public boolean requiresAttention() {
        return isHierarchyAnomaly() || duplicateName;
    }
}
