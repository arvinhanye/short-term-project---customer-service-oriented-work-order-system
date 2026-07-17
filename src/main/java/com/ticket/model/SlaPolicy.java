package com.ticket.model;

public class SlaPolicy {
    private Long policyId;
    private String policyName;
    private String priority;
    private int firstResponseMinutes;
    private int nextResponseMinutes;
    private int resolutionMinutes;
    private boolean businessHoursOnly;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String value) { policyName = value; }
    public String getPriority() { return priority; }
    public void setPriority(String value) { priority = value; }
    public int getFirstResponseMinutes() { return firstResponseMinutes; }
    public void setFirstResponseMinutes(int value) { firstResponseMinutes = value; }
    public int getNextResponseMinutes() { return nextResponseMinutes; }
    public void setNextResponseMinutes(int value) { nextResponseMinutes = value; }
    public int getResolutionMinutes() { return resolutionMinutes; }
    public void setResolutionMinutes(int value) { resolutionMinutes = value; }
    public boolean isBusinessHoursOnly() { return businessHoursOnly; }
    public void setBusinessHoursOnly(boolean value) { businessHoursOnly = value; }
}
