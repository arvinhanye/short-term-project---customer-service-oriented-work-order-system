package com.ticket.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DBConfigTest {
    @Test
    void shouldMapPropertyNameToScopedEnvironmentVariable() {
        Assertions.assertEquals("TICKET_MYSQL_WRITE_PASSWORD",
            DBConfig.toEnvironmentKey("mysql.write.password"));
        Assertions.assertEquals("TICKET_MONGODB_URI", DBConfig.toEnvironmentKey("mongodb.uri"));
    }
}
