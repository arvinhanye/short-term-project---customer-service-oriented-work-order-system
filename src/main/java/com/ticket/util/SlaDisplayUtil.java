package com.ticket.util;

import com.ticket.model.Order;
import java.time.Duration;
import java.time.LocalDateTime;

public final class SlaDisplayUtil {
    private SlaDisplayUtil() { }

    public static LocalDateTime nextDueAt(Order order) {
        if (order == null || order.getSlaPausedAt() != null || order.getResolvedAt() != null
                || "CANCELLED".equals(order.getSlaState())) return null;
        if (order.getFirstRespondedAt() == null && order.getFirstResponseDueAt() != null) {
            return earliest(order.getFirstResponseDueAt(), order.getResolutionDueAt());
        }
        return earliest(order.getNextResponseDueAt(), order.getResolutionDueAt());
    }

    public static LocalDateTime breachedDueAt(Order order, LocalDateTime now) {
        if (order == null || now == null || order.getSlaPausedAt() != null
                || "CANCELLED".equals(order.getSlaState())) return null;
        LocalDateTime missed = null;
        LocalDateTime firstDue = order.getFirstResponseDueAt();
        if (firstDue != null && ((order.getFirstRespondedAt() == null && now.isAfter(firstDue))
                || (order.getFirstRespondedAt() != null && order.getFirstRespondedAt().isAfter(firstDue)))) {
            missed = firstDue;
        }
        if (order.getNextResponseDueAt() != null && now.isAfter(order.getNextResponseDueAt())) {
            missed = earliest(missed, order.getNextResponseDueAt());
        }
        if (order.getResolutionDueAt() != null && now.isAfter(order.getResolutionDueAt())) {
            missed = earliest(missed, order.getResolutionDueAt());
        }
        return missed;
    }

    public static String countdown(Order order) {
        if (order != null && order.getSlaPausedAt() != null) return "已暂停";
        if (order != null && "BREACHED".equals(order.getSlaState())) return "已超时";
        LocalDateTime dueAt = nextDueAt(order);
        if (dueAt == null) {
            return switch (order == null || order.getSlaState() == null ? "" : order.getSlaState()) {
                case "MET" -> "已达标";
                case "BREACHED" -> "已超时";
                case "CANCELLED" -> "已取消";
                default -> "—";
            };
        }
        Duration duration = Duration.between(LocalDateTime.now(), dueAt);
        boolean overdue = duration.isNegative();
        duration = duration.abs();
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        String time = hours > 0 ? hours + "小时" + minutes + "分" : Math.max(1, minutes) + "分";
        return overdue ? "已超时 " + time : "剩余 " + time;
    }

    private static LocalDateTime earliest(LocalDateTime left, LocalDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }
}
