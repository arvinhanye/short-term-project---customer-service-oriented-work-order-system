package com.ticket.dao;

import com.ticket.exception.BusinessException;
import com.ticket.service.BusinessService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ItemDAOTest {
    @Test
    void shouldRejectIllegalStatusTransition() {
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(3, 1));
    }
}
