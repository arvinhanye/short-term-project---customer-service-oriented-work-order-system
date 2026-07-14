package com.ticket.dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HealthCheckDTOTest {
    @Test
    void keepsStructuredResultsAndLegacySummariesInSync() {
        HealthCheckDTO result = new HealthCheckDTO();

        result.pass("MySQL 写库连接", 12L);
        result.fail("MongoDB 连接", new IllegalStateException("连接超时"), 35L);

        Assertions.assertFalse(result.isHealthy());
        Assertions.assertEquals(1, result.getPassedChecks().size());
        Assertions.assertEquals(1, result.getFailedChecks().size());
        Assertions.assertEquals(2, result.getCheckResults().size());
        Assertions.assertEquals(47L, result.getTotalDurationMillis());
        Assertions.assertEquals("连接超时", result.getCheckResults().get(1).message());
    }
}
