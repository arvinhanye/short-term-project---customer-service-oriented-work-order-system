package com.ticket.util;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/** 课程项目统一工作时间：周一至周五 09:00-18:00。 */
public final class BusinessTimeUtil {
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(18, 0);

    private BusinessTimeUtil() { }

    public static LocalDateTime addMinutes(LocalDateTime start, int minutes, boolean businessHoursOnly) {
        if (start == null) throw new IllegalArgumentException("start is required");
        if (minutes < 0) throw new IllegalArgumentException("minutes must not be negative");
        if (!businessHoursOnly) return start.plusMinutes(minutes);
        LocalDateTime cursor = normalize(start);
        int remaining = minutes;
        while (remaining > 0) {
            LocalDateTime endOfDay = cursor.toLocalDate().atTime(END);
            long available = Math.max(0, ChronoUnit.MINUTES.between(cursor, endOfDay));
            if (remaining <= available) return cursor.plusMinutes(remaining);
            remaining -= (int) available;
            cursor = nextBusinessDay(cursor.plusDays(1).toLocalDate().atTime(START));
        }
        return cursor;
    }

    private static LocalDateTime normalize(LocalDateTime value) {
        LocalDateTime cursor = nextBusinessDay(value);
        if (cursor.toLocalTime().isBefore(START)) return cursor.toLocalDate().atTime(START);
        if (!cursor.toLocalTime().isBefore(END)) {
            return nextBusinessDay(cursor.plusDays(1).toLocalDate().atTime(START));
        }
        return cursor;
    }

    private static LocalDateTime nextBusinessDay(LocalDateTime value) {
        LocalDateTime cursor = value;
        while (cursor.getDayOfWeek() == DayOfWeek.SATURDAY
                || cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
            cursor = cursor.plusDays(1).toLocalDate().atTime(START);
        }
        return cursor;
    }
}
