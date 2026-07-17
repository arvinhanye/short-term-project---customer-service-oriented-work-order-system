package com.ticket.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BusinessTimeUtilTest {
    @Test
    void carriesBusinessMinutesAcrossEveningAndWeekend() {
        LocalDateTime friday = LocalDateTime.of(2026, 7, 17, 17, 30);
        assertEquals(LocalDateTime.of(2026, 7, 20, 10, 0),
            BusinessTimeUtil.addMinutes(friday, 90, true));
    }

    @Test
    void normalizesTimeOutsideBusinessHours() {
        assertEquals(LocalDateTime.of(2026, 7, 20, 9, 30),
            BusinessTimeUtil.addMinutes(LocalDateTime.of(2026, 7, 18, 12, 0), 30, true));
        assertEquals(LocalDateTime.of(2026, 7, 16, 20, 30),
            BusinessTimeUtil.addMinutes(LocalDateTime.of(2026, 7, 16, 20, 0), 30, false));
    }
}
