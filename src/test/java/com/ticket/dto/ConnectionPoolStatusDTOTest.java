package com.ticket.dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectionPoolStatusDTOTest {
    @Test
    void derivesUsageAvailabilityAndOperationalState() {
        ConnectionPoolStatusDTO status = new ConnectionPoolStatusDTO();
        status.setMaximumPoolSize(10);
        status.setActiveConnections(8);

        Assertions.assertEquals(80, status.getUsagePercent());
        Assertions.assertEquals(2, status.getAvailableConnections());
        Assertions.assertEquals("负载较高", status.getStatusText());

        status.setThreadsAwaitingConnection(1);
        Assertions.assertEquals("存在等待", status.getStatusText());
    }
}
