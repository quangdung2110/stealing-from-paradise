package com.flashsale.flashsaleservice.service;

import com.flashsale.commonlib.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlashSaleTimeValidatorTest {

    private final FlashSaleTimeValidator validator = new FlashSaleTimeValidator();

    @Test
    void validTimeWindowShouldPass() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(4);
        assertDoesNotThrow(() -> validator.validate(start, end));
    }

    @Test
    void startTimeTooSoonShouldFail() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(30);
        LocalDateTime end = start.plusHours(1);
        assertThrows(AppException.class, () -> validator.validate(start, end));
    }

    @Test
    void endBeforeStartShouldFail() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.minusHours(1);
        assertThrows(AppException.class, () -> validator.validate(start, end));
    }

    @Test
    void durationExceeds24HoursShouldFail() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(25);
        assertThrows(AppException.class, () -> validator.validate(start, end));
    }

    @Test
    void nullStartShouldFail() {
        assertThrows(AppException.class, () -> validator.validate(null, LocalDateTime.now().plusHours(2)));
    }
}
