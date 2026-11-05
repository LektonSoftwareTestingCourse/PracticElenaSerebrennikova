package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.*;
import com.processing.cardmanagement.exceptions.CardNotFoundException;
import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import com.processing.cardmanagement.exceptions.ReservationAlreadyExistsException;
import com.processing.cardmanagement.exceptions.ReservationNotFoundException;
import com.processing.cardmanagement.models.*;
import com.processing.cardmanagement.options.CardServiceDefaults;
import com.processing.cardmanagement.options.CardServiceSettings;
import com.processing.cardmanagement.repositories.*;
import com.processing.cardmanagement.services.retries.RetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {
    private static final String PAN = "4000001234567890";
    private static final String RRN = "123456789012";
    @Mock private CardRepository cards;
    @Mock private ReservationRepository reservations;
    @Mock private ReservationRollbackRepository rollbacks;
    @Mock private CardServiceSettings settings;
    @Mock private CardServiceDefaults defaults;
    @Mock private PanGenerator pans;
    @Mock private CardEventNotifier events;
    @Mock private BinIssuerService bins;
    @Mock private RetryService retries;
    @Mock private TransactionRunner transactions;
    private CardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CardServiceImpl(cards, reservations, rollbacks, settings, defaults, pans,
                events, bins, retries, transactions);
    }

    @Test
    void shouldReturnCardWhenPanExists() {
        // Arrange
        Card card = activeCard();
        when(cards.findByPan(PAN)).thenReturn(Optional.of(card));
        // Act
        Card actual = service.getCard(PAN);
        // Assert
        assertThat(actual).isEqualTo(card);
    }

    @Test
    void shouldThrowWhenCardDoesNotExist() {
        // Arrange
        when(cards.findByPan(PAN)).thenReturn(Optional.empty());
        // Act / Assert
        assertThatThrownBy(() -> service.getCard(PAN)).isInstanceOf(CardNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "1000"})
    void shouldReduceBalanceWhenReservationFitsAvailableFunds(String amount) {
        // Arrange
        runTransactionsInline();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(activeCard()));
        when(reservations.isUnique(RRN, PAN)).thenReturn(true);
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cards.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Act
        Card actual = service.reserve(PAN, new BigDecimal(amount), RRN);
        // Assert
        assertThat(actual.availableBalance()).isEqualByComparingTo(new BigDecimal("1000").subtract(new BigDecimal(amount)));
        verify(events).notifyListeners(any(CardServiceReserveEvent.class));
        verify(cards).update(actual);
    }

    @Test
    void shouldRejectWhenReservationExceedsBalance() {
        // Arrange
        runTransactionsInline();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(activeCard()));
        // Act / Assert
        assertThatThrownBy(() -> service.reserve(PAN, new BigDecimal("1001"), RRN))
                .isInstanceOf(InsufficientFundsException.class);
        verify(cards, never()).update(any());
        verify(reservations, never()).save(any());
    }

    @Test
    void shouldRejectWhenReservationAlreadyExists() {
        // Arrange
        runTransactionsInline();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(activeCard()));
        when(reservations.isUnique(RRN, PAN)).thenReturn(false);
        // Act / Assert
        assertThatThrownBy(() -> service.reserve(PAN, BigDecimal.ONE, RRN))
                .isInstanceOf(ReservationAlreadyExistsException.class);
        verify(cards, never()).update(any());
    }

    @ParameterizedTest
    @EnumSource(value = CardStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void shouldRejectReservationWhenCardIsNotActive(CardStatus status) {
        // Arrange
        runTransactionsInline();
        Card card = activeCard().withData(status, new BigDecimal("10000"),
                new BigDecimal("50000"), new BigDecimal("1000"));
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card));
        // Act / Assert
        assertThatThrownBy(() -> service.reserve(PAN, BigDecimal.ONE, RRN)).isInstanceOf(IllegalStateException.class);
        verify(cards, never()).update(any());
    }

    @Test
    void shouldRestoreBalanceWhenReservationIsRolledBack() {
        // Arrange
        runTransactionsInline();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(activeCard()));
        when(reservations.findByRrnAndPanForUpdate(RRN, PAN))
                .thenReturn(Optional.of(new Reservation(PAN, new BigDecimal("100"), RRN)));
        when(rollbacks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cards.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Act
        Card actual = service.rollback(PAN, new BigDecimal("100"), RRN);
        // Assert
        assertThat(actual.availableBalance()).isEqualByComparingTo("1100");
        verify(reservations).save(argThat(value -> value.status() == ReservationStatus.ROLLED_BACK));
        verify(events).notifyListeners(any(CardServiceRollbackEvent.class));
    }

    @Test
    void shouldRejectRollbackWhenReservationIsMissing() {
        // Arrange
        runTransactionsInline();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(activeCard()));
        when(reservations.findByRrnAndPanForUpdate(RRN, PAN)).thenReturn(Optional.empty());
        // Act / Assert
        assertThatThrownBy(() -> service.rollback(PAN, BigDecimal.ONE, RRN))
                .isInstanceOf(ReservationNotFoundException.class);
        verify(cards, never()).update(any());
    }

    @Test
    void shouldPreserveUnspecifiedFieldsWhenPatchingStatus() {
        // Arrange
        runTransactionsInline();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(activeCard()));
        when(cards.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Act
        Card actual = service.patchCard(PAN, CardStatus.BLOCKED, null, null, null);
        // Assert
        assertThat(actual.status()).isEqualTo(CardStatus.BLOCKED);
        assertThat(actual).usingRecursiveComparison().ignoringFields("status").isEqualTo(activeCard());
        verify(events).notifyListeners(any(CardServicePatchEvent.class));
    }

    private void runTransactionsInline() {
        when(transactions.runSupplier(any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(0);
            return operation.get();
        });
    }

    private static Card activeCard() {
        return new Card(new UUID(0, 1), PAN, "400000", "IVAN IVANOV", YearMonth.of(2027, 12),
                CardStatus.ACTIVE, "643", new BigDecimal("10000"), new BigDecimal("50000"),
                new BigDecimal("1000"), "ISS001", Instant.parse("2026-10-23T12:00:00Z"));
    }
}
