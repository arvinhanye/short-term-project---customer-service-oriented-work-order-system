package com.ticket.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HealthCheckDTO {
    private final LocalDateTime checkedAt = LocalDateTime.now();
    private boolean healthy = true;
    private final List<String> passedChecks = new ArrayList<>();
    private final List<String> failedChecks = new ArrayList<>();
    private final List<CheckResult> checkResults = new ArrayList<>();

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

    public List<CheckResult> getCheckResults() {
        return checkResults;
    }

    public void pass(String checkName) {
        pass(checkName, 0L);
    }

    public void pass(String checkName, long durationMillis) {
        passedChecks.add(checkName);
        checkResults.add(new CheckResult(checkName, true, Math.max(0L, durationMillis), "检查通过"));
    }

    public void fail(String checkName, Exception ex) {
        fail(checkName, ex, 0L);
    }

    public void fail(String checkName, Exception ex, long durationMillis) {
        healthy = false;
        String message = errorMessage(ex);
        failedChecks.add(checkName + ": " + message);
        checkResults.add(new CheckResult(checkName, false, Math.max(0L, durationMillis), message));
    }

    public long getTotalDurationMillis() {
        return checkResults.stream().mapToLong(CheckResult::durationMillis).sum();
    }

    private String errorMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Override
    public String toString() {
        return "checkedAt=" + checkedAt
            + "\nhealthy=" + healthy
            + "\npassedChecks=" + passedChecks
            + "\nfailedChecks=" + failedChecks;
    }

    public record CheckResult(String name, boolean passed, long durationMillis, String message) {
    }
}
