package com.ticket.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/** Consistent human-readable time formatting for all desktop UI views. */
public final class TimeFormatUtil {
    public static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm '北京时间'");

    private TimeFormatUtil() {
    }

    public static String format(LocalDateTime value) {
        return value == null ? "—" : DISPLAY_FORMATTER.format(value);
    }

    public static String format(Instant value) {
        return value == null ? "—" : DISPLAY_FORMATTER.format(value.atZone(BEIJING_ZONE));
    }

    public static String format(Date value) {
        return value == null ? "—" : format(value.toInstant());
    }
}
