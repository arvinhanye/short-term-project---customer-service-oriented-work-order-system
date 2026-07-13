package com.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
