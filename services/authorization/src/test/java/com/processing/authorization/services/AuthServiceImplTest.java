package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.events.*;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    private static final String PAN = "4000001234567890";
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-10-31T23:59:59Z");
    @Mock private LimitUsageRepository limits;
    @Mock private AuthorizationEventNotifier events;
    @Mock private BinLookupClient bins;
    @Mock private CardManagementClient cards;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(limits, events, bins, cards);
    }

    @ParameterizedTest
    @CsvSource({"999, APPROVED, 00", "1000, APPROVED, 00"})
    void shouldRespectBalanceBoundaryWhenAuthorizing(String amount, String status, String code) {
        // Arrange
        stubCard(CardModelStatus.ACTIVE, YearMonth.of(2026, 10));
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        // Act
        AuthorizationResponse actual = service.authorize(request(amount), Instant.now());
        // Assert
        assertThat(actual.status()).isEqualTo(status);
        assertThat(actual.responseCode()).isEqualTo(code);
        verify(events).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenAmountExceedsBalanceByOne() {
        // Arrange
        stubCard(CardModelStatus.ACTIVE, YearMonth.of(2026, 10));
        // Act
        AuthorizationResponse actual = service.authorize(request("1001"), Instant.now());
        // Assert
        assertDecline(actual, "INSUFFICIENT_FUNDS", "51");
        verify(cards, never()).reserve(any(), any(), any());
        verify(events).notify(any(AuthServiceAuthDeclinedFundsEvent.class));
    }

    @Test
    void shouldReserveFundsWhenAuthorizationIsApproved() {
        // Arrange
        stubCard(CardModelStatus.ACTIVE, YearMonth.of(2026, 10));
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        // Act
        AuthorizationResponse actual = service.authorize(request("1000"), Instant.now());
        // Assert
        assertThat(actual.status()).isEqualTo("APPROVED");
        assertThat(actual.rrn()).matches("\\d{12}");
        assertThat(actual.authCode()).matches("[A-Z0-9]{6}");
        verify(cards).reserve(new BigDecimal("1000"), actual.rrn(), PAN);
        verify(events).notify(any(AuthServiceAuthorizeEvent.class));
    }

    @ParameterizedTest
    @CsvSource({"BLOCKED, CARD_BLOCKED, 05", "INACTIVE, CARD_INACTIVE, 05", "EXPIRED, CARD_EXPIRED, 54"})
    void shouldDeclineWhenCardIsNotActive(CardModelStatus status, String reason, String code) {
        // Arrange
        stubCard(status, YearMonth.of(2026, 10));
        // Act
        AuthorizationResponse actual = service.authorize(request("1000"), Instant.now());
        // Assert
        assertDecline(actual, reason, code);
        verify(cards, never()).reserve(any(), any(), any());
        verify(events).notify(any(AuthServiceAuthDeclinedCardStatusEvent.class));
    }

    @Test
    void shouldDeclineWhenCardHasExpiredBeforeTransmissionDate() {
        // Arrange
        stubCard(CardModelStatus.ACTIVE, YearMonth.of(2026, 9));
        // Act
        AuthorizationResponse actual = service.authorize(request("1000"), Instant.now());
        // Assert
        assertDecline(actual, "CARD_EXPIRED", "54");
        verify(cards, never()).reserve(any(), any(), any());
    }

    @Test
    void shouldDeclineWhenCardIsMissing() {
        // Arrange
        when(cards.getCard(PAN)).thenThrow(new CardNotFoundException(PAN));
        // Act
        AuthorizationResponse actual = service.authorize(request("1000"), Instant.now());
        // Assert
        assertDecline(actual, "CARD_NOT_FOUND", "14");
        verify(events).notify(any(AuthServiceAuthDeclineNoCardEvent.class));
    }

    @Test
    void shouldDeclineWhenLimitWouldBeExceeded() {
        // Arrange
        stubCard(CardModelStatus.ACTIVE, YearMonth.of(2026, 10));
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(0);
        // Act
        AuthorizationResponse actual = service.authorize(request("1000"), Instant.now());
        // Assert
        assertDecline(actual, "EXCEEDS_AMOUNT_LIMIT", "61");
        verify(cards, never()).reserve(any(), any(), any());
    }

    @Test
    void shouldApproveWhenLimitUpdateSucceedsAfterDuplicateKey() {
        // Arrange
        stubCard(CardModelStatus.ACTIVE, YearMonth.of(2026, 10));
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("collision")).thenReturn(1);
        // Act
        AuthorizationResponse actual = service.authorize(request("1000"), Instant.now());
        // Assert
        assertThat(actual.status()).isEqualTo("APPROVED");
        verify(cards).reserve(new BigDecimal("1000"), actual.rrn(), PAN);
        verify(events).notify(any(AuthServiceDuplicateKeyEvent.class));
    }

    @Test
    void shouldDeclineWhenLimitsDatabaseIsUnavailable() {
        // Arrange
        stubCard(CardModelStatus.ACTIVE, YearMonth.of(2026, 10));
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("database down"));
        // Act
        AuthorizationResponse actual = service.authorize(request("1000"), Instant.now());
        // Assert
        assertDecline(actual, "DB_UNAVAILABLE", "96");
        verify(cards, never()).reserve(any(), any(), any());
    }

    private void stubCard(CardModelStatus status, YearMonth expiry) {
        when(cards.getCard(PAN)).thenReturn(new CardModel(new UUID(0, 1), PAN, "400000", "IVAN IVANOV",
                expiry, status, "643", new BigDecimal("10000"), new BigDecimal("50000"),
                new BigDecimal("1000"), "ISS001", TRANSMISSION_TIME));
        when(bins.getIssuerId(PAN)).thenReturn(Optional.empty());
    }

    private static AuthorizationRequest request(String amount) {
        return new AuthorizationRequest("0100", "000001", PAN, "000000", new BigDecimal(amount), "643",
                TRANSMISSION_TIME, "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", "ISS001");
    }

    private static void assertDecline(AuthorizationResponse actual, String reason, String code) {
        assertThat(actual.status()).isEqualTo("DECLINED");
        assertThat(actual.declineReason()).isEqualTo(reason);
        assertThat(actual.responseCode()).isEqualTo(code);
    }
}
