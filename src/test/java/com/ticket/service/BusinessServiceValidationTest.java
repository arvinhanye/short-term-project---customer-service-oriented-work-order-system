package com.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BusinessServiceValidationTest {
    @Test
    void rejectsBlankRequiredDescriptionBeforeDatabaseAccess() {
        User actor = new User();
        actor.setUserId(10001L);
        actor.setStatus(1);
        actor.setRole("USER");

        BusinessException exception = assertThrows(BusinessException.class,
            () -> new BusinessService().createTicket(actor, "测试工单", null, BigDecimal.ZERO, "  ", "MEDIUM"));

        assertEquals("问题描述不能为空", exception.getMessage());
    }

    @Test
    void transferInvitationRequiresDifferentTargetOwnershipAndReason() {
        User admin = staff(10003L, "ADMIN");

        assertEquals("需要专业协同处理",
            BusinessService.validateTransferRequest(admin, "10003", null, 10013L, " 需要专业协同处理 "));
        assertThrows(BusinessException.class,
            () -> BusinessService.validateTransferRequest(admin, "10003", null, 10013L, "  "));
        assertThrows(BusinessException.class,
            () -> BusinessService.validateTransferRequest(admin, "10013", null, 10014L, "重新分配"));
        assertThrows(BusinessException.class,
            () -> BusinessService.validateTransferRequest(admin, "10003", null, 10003L, "转给自己"));
        assertThrows(BusinessException.class,
            () -> BusinessService.validateTransferRequest(admin, "10003", "10014", 10013L, "重复邀请"));
    }

    @Test
    void unassignedTicketCanInviteAnotherAdminButStillRequiresReason() {
        User admin = staff(10003L, "ADMIN");

        assertDoesNotThrow(() ->
            BusinessService.validateTransferRequest(admin, null, null, 10013L, "请协助处理"));
        assertThrows(BusinessException.class, () ->
            BusinessService.validateTransferRequest(admin, null, null, 10013L, null));
    }

    @Test
    void statusFlowDoesNotAllowSkippingProcessingOrClosingBeforeCompletion() {
        assertDoesNotThrow(() -> BusinessService.validateStatusTransition(0, 1));
        assertDoesNotThrow(() -> BusinessService.validateStatusTransition(0, 4));
        assertDoesNotThrow(() -> BusinessService.validateStatusTransition(1, 2));
        assertDoesNotThrow(() -> BusinessService.validateStatusTransition(1, 4));
        assertDoesNotThrow(() -> BusinessService.validateStatusTransition(2, 3));

        assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(0, 2));
        assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(0, 3));
        assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(1, 3));
        assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(3, 1));
    }

    @Test
    void remindersAreLimitedToPendingAndProcessingTickets() {
        assertDoesNotThrow(() -> BusinessService.requireReminderEligibleStatus(0));
        assertDoesNotThrow(() -> BusinessService.requireReminderEligibleStatus(1));
        assertThrows(BusinessException.class, () -> BusinessService.requireReminderEligibleStatus(2));
        assertThrows(BusinessException.class, () -> BusinessService.requireReminderEligibleStatus(3));
        assertThrows(BusinessException.class, () -> BusinessService.requireReminderEligibleStatus(4));
    }

    private User staff(long userId, String role) {
        User user = new User();
        user.setUserId(userId);
        user.setStatus(1);
        user.setRole(role);
        return user;
    }
}
