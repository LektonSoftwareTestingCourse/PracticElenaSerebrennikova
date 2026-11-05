package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRequestValidatorTest {
    private final TransactionRequestValidator validator = new TransactionRequestValidator();

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "1000000"})
    void shouldAcceptWhenRequestIsValid(String amount) {
        // Arrange
        AuthorizationRequest request = validRequest().amount(new BigDecimal(amount)).build();
        // Act / Assert
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "0", "-0.01", "-1"}, nullValues = "NULL")
    void shouldRejectWhenAmountIsMissingOrNotPositive(BigDecimal amount) {
        // Arrange
        AuthorizationRequest request = validRequest().amount(amount).build();
        // Act / Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class).hasMessageContaining("amount");
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidRequests")
    void shouldRejectWhenRequiredFieldIsInvalid(AuthorizationRequest request, String field) {
        // Act / Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class).hasMessageContaining(field);
    }

    @Test
    void shouldRejectWhenRequestBodyIsMissing() {
        // Act / Assert
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(TransactionValidationException.class).hasMessage("Request body is required");
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(validRequest().mti("0200").build(), "mti"),
                Arguments.of(validRequest().mti(null).build(), "mti"),
                Arguments.of(validRequest().stan(" ").build(), "stan"),
                Arguments.of(validRequest().pan("400000123456789").build(), "pan"),
                Arguments.of(validRequest().pan("40000012345678901").build(), "pan"),
                Arguments.of(validRequest().pan("400000123456789X").build(), "pan"),
                Arguments.of(validRequest().processingCode(null).build(), "processingCode"),
                Arguments.of(validRequest().currencyCode("64").build(), "currencyCode"),
                Arguments.of(validRequest().currencyCode("6430").build(), "currencyCode"),
                Arguments.of(validRequest().transmissionDateTime(null).build(), "transmissionDateTime"),
                Arguments.of(validRequest().terminalId("").build(), "terminalId"),
                Arguments.of(validRequest().merchantId(null).build(), "merchantId"),
                Arguments.of(validRequest().mcc("541").build(), "mcc"),
                Arguments.of(validRequest().mcc("54110").build(), "mcc"),
                Arguments.of(validRequest().mcc("ABCD").build(), "mcc"),
                Arguments.of(validRequest().acquirerId(" ").build(), "acquirerId"));
    }

    private static AuthorizationRequest.AuthorizationRequestBuilder validRequest() {
        return AuthorizationRequest.builder().mti("0100").stan("000001").pan("4000001234567890")
                .processingCode("000000").amount(BigDecimal.ONE).currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-10-23T12:00:00Z"))
                .terminalId("TERM0001").merchantId("MERCH00000000001").mcc("5411").acquirerId("ACQ001");
    }
}
