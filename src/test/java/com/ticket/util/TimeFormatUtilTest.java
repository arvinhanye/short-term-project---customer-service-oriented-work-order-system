package com.ticket.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TimeFormatUtilTest {
    @Test
    void formatsLocalDatabaseTimeAsBeijingDisplayTime() {
        assertEquals("2026-07-09 15:12 北京时间",
            TimeFormatUtil.format(LocalDateTime.of(2026, 7, 9, 15, 12, 45)));
    }

    @Test
    void convertsInstantToBeijingTime() {
        assertEquals("2026-07-09 15:12 北京时间",
            TimeFormatUtil.format(Instant.parse("2026-07-09T07:12:45Z")));
    }
}
