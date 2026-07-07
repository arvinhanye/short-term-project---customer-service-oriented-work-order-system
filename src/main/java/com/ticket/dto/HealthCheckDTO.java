package com.ticket.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HealthCheckDTO {
    private final LocalDateTime checkedAt = LocalDateTime.now();
    private boolean healthy = true;
    private final List<String> passedChecks = new ArrayList<>();
    private final List<String> failedChecks = new ArrayList<>();

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public List<String> getPassedChecks() {
        return passedChecks;
    }

    public List<String> getFailedChecks() {
        return failedChecks;
    }

    public void pass(String checkName) {
        passedChecks.add(checkName);
    }

    public void fail(String checkName, Exception ex) {
        healthy = false;
        failedChecks.add(checkName + ": " + ex.getMessage());
    }

    @Override
    public String toString() {
        return "checkedAt=" + checkedAt
            + "\nhealthy=" + healthy
            + "\npassedChecks=" + passedChecks
            + "\nfailedChecks=" + failedChecks;
    }
}
