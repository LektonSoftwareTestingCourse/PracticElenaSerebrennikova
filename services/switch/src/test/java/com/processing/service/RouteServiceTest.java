package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {
    @Mock private RoutingService routing;
    @Mock private AuthorizationClient authorization;
    @Mock private AcquiringFeeClient fees;
    @Mock private LoggerClient logger;
    private RouteService service;

    @BeforeEach
    void setUp() {
        service = new RouteService(routing, authorization, fees, logger);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL, 0", "17, 17"}, nullValues = "NULL")
    void shouldPublishEnrichedTransactionWhenAuthorizationIsApproved(Integer processingTime, int expectedTime) {
        // Arrange
        AuthorizationRequest request = SwitchFixtures.request("4000001234567890");
        AuthorizationResponse approved = response("APPROVED", processingTime);
        when(routing.getIssuerIdByPan(request.pan())).thenReturn("ISS001");
        when(authorization.authorize(any())).thenReturn(approved);
        when(fees.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(new BigDecimal("15"));
        when(logger.log(any())).thenReturn(true);
        // Act
        AuthorizationResponse actual = service.route(request);
        // Assert
        assertThat(actual).isEqualTo(approved);
        ArgumentCaptor<TransactionRequest> published = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(logger).log(published.capture());
        assertThat(published.getValue()).extracting(TransactionRequest::issuerId, TransactionRequest::terminalId,
                TransactionRequest::merchantId, TransactionRequest::status, TransactionRequest::rrn)
                .containsExactly("ISS001", "TERM1000", "SHOP00000000000", TransactionStatus.APPROVED, "123456789012");
        assertThat(published.getValue().acquiringFee()).isEqualByComparingTo("15");
        assertThat(published.getValue().processingTimeMs()).isEqualTo(expectedTime);
        verify(authorization, never()).rollback(any(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "40000", "9999991234567890"})
    void shouldDeclineWhenBinCannotBeRouted(String pan) {
        // Arrange
        when(routing.getIssuerIdByPan(pan)).thenThrow(new UnknownBinException("unknown"));
        when(logger.log(any())).thenReturn(true);
        // Act
        AuthorizationResponse actual = service.route(SwitchFixtures.request(pan));
        // Assert
        assertThat(actual).isEqualTo(AuthorizationResponse.unknownBin("000001"));
        verify(authorization, never()).rollback(any(), any());
        verify(logger).log(any(TransactionRequest.class));
    }

    @Test
    void shouldDeclineWhenAuthorizationIsUnavailable() {
        // Arrange
        when(routing.getIssuerIdByPan(any())).thenReturn("ISS001");
        when(authorization.authorize(any())).thenThrow(new AuthorizationException("000001", 1, "timeout"));
        when(logger.log(any())).thenReturn(true);
        // Act
        AuthorizationResponse actual = service.route(SwitchFixtures.request("4000001234567890"));
        // Assert
        assertThat(actual).isEqualTo(AuthorizationResponse.authUnavailable("000001"));
        verify(authorization, never()).rollback(any(), any());
    }

    @ParameterizedTest
    @MethodSource("rollbackResponses")
    void shouldRollbackAndReturnSystemErrorWhenPublishingApprovedTransactionFails(RollbackResponse rollback) {
        // Arrange
        when(routing.getIssuerIdByPan(any())).thenReturn("ISS001");
        when(authorization.authorize(any())).thenReturn(response("APPROVED", 0));
        when(logger.log(any())).thenReturn(false);
        when(authorization.rollback(any(), eq("123456789012"))).thenReturn(rollback);
        // Act
        AuthorizationResponse actual = service.route(SwitchFixtures.request("4000001234567890"));
        // Assert
        assertThat(actual).isEqualTo(AuthorizationResponse.systemError("000001"));
        verify(authorization).rollback(
                eq(AuthorizationRequestNormalizer.normalize(SwitchFixtures.request("4000001234567890"))
                        .withIssuerId("ISS001")), eq("123456789012"));
    }

    @Test
    void shouldPreserveDeclineWhenPublishingDeclinedTransactionFails() {
        // Arrange
        AuthorizationResponse declined = response("DECLINED", 0);
        when(routing.getIssuerIdByPan(any())).thenReturn("ISS001");
        when(authorization.authorize(any())).thenReturn(declined);
        when(logger.log(any())).thenReturn(false);
        // Act
        AuthorizationResponse actual = service.route(SwitchFixtures.request("4000001234567890"));
        // Assert
        assertThat(actual).isEqualTo(declined);
        verify(authorization, never()).rollback(any(), any());
    }

    private static Stream<RollbackResponse> rollbackResponses() {
        return Stream.of(null, new RollbackResponse("123456789012", "00", "APPROVED", null, 0),
                new RollbackResponse("123456789012", "05", "DECLINED", "ALREADY_ROLLED_BACK", 0));
    }

    private static AuthorizationResponse response(String status, Integer processingTime) {
        return new AuthorizationResponse("0110", "000001", "123456789012", "AUTH01", "00", status, null, processingTime);
    }
}
