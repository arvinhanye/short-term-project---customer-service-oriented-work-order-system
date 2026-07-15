package com.ticket.service;

import com.ticket.exception.BusinessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StatisticsServiceTest {
    @Test
    void shouldRejectInvalidTransitionFromCompletedToProcessing() {
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(2, 1));
    }

    @Test
    void shouldAcceptSupportedStatusTransitions() {
        Assertions.assertDoesNotThrow(() -> BusinessService.validateStatusTransition(0, 1));
        Assertions.assertDoesNotThrow(() -> BusinessService.validateStatusTransition(0, 4));
        Assertions.assertDoesNotThrow(() -> BusinessService.validateStatusTransition(1, 2));
        Assertions.assertDoesNotThrow(() -> BusinessService.validateStatusTransition(1, 4));
        Assertions.assertDoesNotThrow(() -> BusinessService.validateStatusTransition(2, 3));
    }

    @Test
    void shouldRejectStatusSkipping() {
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(0, 2));
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(0, 3));
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(1, 3));
    }

    @Test
    void shouldRejectClosedAndCancelledTransitions() {
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(3, 1));
        Assertions.assertThrows(BusinessException.class, () -> BusinessService.validateStatusTransition(4, 1));
    }
}
