package com.ticket.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ticket.model.Order;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SlaDisplayUtilTest {
    @Test
    void detectsLateFirstResponseEvenWhenResolutionIsStillInTheFuture() {
        LocalDateTime firstDue = LocalDateTime.of(2026, 7, 16, 10, 0);
        Order order = new Order();
        order.setFirstResponseDueAt(firstDue);
        order.setFirstRespondedAt(firstDue.plusMinutes(5));
        order.setResolutionDueAt(firstDue.plusHours(8));

        assertEquals(firstDue, SlaDisplayUtil.breachedDueAt(order, firstDue.plusMinutes(10)));
    }

    @Test
    void returnsNullWhileEveryActiveClockIsStillWithinTarget() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 10, 0);
        Order order = new Order();
        order.setFirstResponseDueAt(now.plusMinutes(30));
        order.setResolutionDueAt(now.plusHours(4));

        assertNull(SlaDisplayUtil.breachedDueAt(order, now));
    }

    @Test
    void hidesCountdownAndBreachDetectionWhilePaused() {
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setSlaState("ACTIVE");
        order.setSlaPausedAt(now.minusHours(1));
        order.setFirstResponseDueAt(now.minusMinutes(30));

        assertEquals("已暂停", SlaDisplayUtil.countdown(order));
        assertNull(SlaDisplayUtil.nextDueAt(order));
        assertNull(SlaDisplayUtil.breachedDueAt(order, now));
    }
}
