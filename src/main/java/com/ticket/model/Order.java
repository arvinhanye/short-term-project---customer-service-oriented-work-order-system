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
    private Long slaPolicyId;
    private LocalDateTime firstResponseDueAt;
    private LocalDateTime nextResponseDueAt;
    private LocalDateTime resolutionDueAt;
    private LocalDateTime firstRespondedAt;
    private LocalDateTime lastAdminResponseAt;
    private LocalDateTime resolvedAt;
    private String slaState;
    private LocalDateTime slaPausedAt;
    private String slaPauseReason;
    private int totalSlaPausedMinutes;
    private LocalDateTime reopenDeadlineAt;
    private int reopenCount;
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

    public Long getSlaPolicyId() { return slaPolicyId; }
    public void setSlaPolicyId(Long slaPolicyId) { this.slaPolicyId = slaPolicyId; }
    public LocalDateTime getFirstResponseDueAt() { return firstResponseDueAt; }
    public void setFirstResponseDueAt(LocalDateTime value) { this.firstResponseDueAt = value; }
    public LocalDateTime getNextResponseDueAt() { return nextResponseDueAt; }
    public void setNextResponseDueAt(LocalDateTime value) { this.nextResponseDueAt = value; }
    public LocalDateTime getResolutionDueAt() { return resolutionDueAt; }
    public void setResolutionDueAt(LocalDateTime value) { this.resolutionDueAt = value; }
    public LocalDateTime getFirstRespondedAt() { return firstRespondedAt; }
    public void setFirstRespondedAt(LocalDateTime value) { this.firstRespondedAt = value; }
    public LocalDateTime getLastAdminResponseAt() { return lastAdminResponseAt; }
    public void setLastAdminResponseAt(LocalDateTime value) { this.lastAdminResponseAt = value; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime value) { this.resolvedAt = value; }
    public String getSlaState() { return slaState; }
    public void setSlaState(String slaState) { this.slaState = slaState; }
    public LocalDateTime getSlaPausedAt() { return slaPausedAt; }
    public void setSlaPausedAt(LocalDateTime value) { this.slaPausedAt = value; }
    public String getSlaPauseReason() { return slaPauseReason; }
    public void setSlaPauseReason(String value) { this.slaPauseReason = value; }
    public int getTotalSlaPausedMinutes() { return totalSlaPausedMinutes; }
    public void setTotalSlaPausedMinutes(int value) { totalSlaPausedMinutes = Math.max(0, value); }
    public LocalDateTime getReopenDeadlineAt() { return reopenDeadlineAt; }
    public void setReopenDeadlineAt(LocalDateTime value) { this.reopenDeadlineAt = value; }
    public int getReopenCount() { return reopenCount; }
    public void setReopenCount(int value) { reopenCount = Math.max(0, value); }

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
