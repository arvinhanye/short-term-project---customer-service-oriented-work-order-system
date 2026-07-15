package com.ticket.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {
    private Long orderId;
    private Long userId;
    private Long itemId;
    private BigDecimal amount;
    private Integer status;
    private Long assignedAdminId;
    private String transferRequestId;
    private Long transferRequestedBy;
    private Long transferTargetAdminId;
    private String transferReason;
    private LocalDateTime transferRequestedAt;
    private int reminderCount;
    private LocalDateTime lastRemindedAt;
    private long workflowVersion;
    private LocalDateTime createdAt;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getAssignedAdminId() {
        return assignedAdminId;
    }

    public void setAssignedAdminId(Long assignedAdminId) {
        this.assignedAdminId = assignedAdminId;
    }

    public String getTransferRequestId() {
        return transferRequestId;
    }

    public void setTransferRequestId(String transferRequestId) {
        this.transferRequestId = transferRequestId;
    }

    public Long getTransferRequestedBy() {
        return transferRequestedBy;
    }

    public void setTransferRequestedBy(Long transferRequestedBy) {
        this.transferRequestedBy = transferRequestedBy;
    }

    public Long getTransferTargetAdminId() {
        return transferTargetAdminId;
    }

    public void setTransferTargetAdminId(Long transferTargetAdminId) {
        this.transferTargetAdminId = transferTargetAdminId;
    }

    public String getTransferReason() {
        return transferReason;
    }

    public void setTransferReason(String transferReason) {
        this.transferReason = transferReason;
    }

    public LocalDateTime getTransferRequestedAt() {
        return transferRequestedAt;
    }

    public void setTransferRequestedAt(LocalDateTime transferRequestedAt) {
        this.transferRequestedAt = transferRequestedAt;
    }

    public int getReminderCount() {
        return reminderCount;
    }

    public void setReminderCount(int reminderCount) {
        this.reminderCount = Math.max(0, reminderCount);
    }

    public LocalDateTime getLastRemindedAt() {
        return lastRemindedAt;
    }

    public void setLastRemindedAt(LocalDateTime lastRemindedAt) {
        this.lastRemindedAt = lastRemindedAt;
    }

    public long getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(long workflowVersion) {
        this.workflowVersion = Math.max(0, workflowVersion);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
