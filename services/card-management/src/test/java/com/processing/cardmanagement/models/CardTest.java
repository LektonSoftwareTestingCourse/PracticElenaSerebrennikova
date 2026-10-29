package com.processing.cardmanagement.models;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {
    @ParameterizedTest
    @CsvSource({"-1, 100", "100, -1", "101, 100"})
    void shouldRejectWhenLimitsAreNegativeOrDailyExceedsMonthly(BigDecimal daily, BigDecimal monthly) {
        // Arrange
        Card card = activeCard();
        // Act / Assert
        assertThatThrownBy(() -> card.withData(CardStatus.ACTIVE, daily, monthly, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "99, 100", "100, 100"})
    void shouldAcceptWhenLimitsAreNonNegativeAndOrdered(BigDecimal daily, BigDecimal monthly) {
        // Arrange
        Card card = activeCard();
        // Act
        Card actual = card.withData(CardStatus.ACTIVE, daily, monthly, BigDecimal.TEN);
        // Assert
        assertThat(actual.dailyLimit()).isEqualByComparingTo(daily);
        assertThat(actual.monthlyLimit()).isEqualByComparingTo(monthly);
        assertThat(card.dailyLimit()).isEqualByComparingTo("1000");
    }

    private static Card activeCard() {
        return new Card(new UUID(0, 1), "4000001234567890", "400000", "IVAN IVANOV", YearMonth.of(2027, 12),
                CardStatus.ACTIVE, "643", new BigDecimal("1000"), new BigDecimal("5000"), BigDecimal.TEN,
                "ISS001", Instant.parse("2026-10-23T12:00:00Z"));
    }
}
