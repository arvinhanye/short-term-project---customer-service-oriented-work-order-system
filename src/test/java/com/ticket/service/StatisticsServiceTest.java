package com.ticket.service;

import com.ticket.exception.BusinessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StatisticsServiceTest {
    @Test
    void shouldRejectInvalidTransitionFromCompletedToProcessing() {
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(2, 1));
    }
}
