package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRequestNormalizerTest {
    @ParameterizedTest
    @CsvSource(value = {"NULL|NULL", "''|''", "'  '|'  '", "T|T0000000",
            "TERM0001|TERM0001", "TERM00012|TERM0001"}, delimiter = '|', nullValues = "NULL")
    void shouldNormalizeTerminalWhenIdentifierHasDifferentLengths(String input, String expected) {
        // Act
        String actual = AuthorizationRequestNormalizer.normalizeTerminalId(input);
        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL|NULL", "''|''", "'  '|'  '", "SHOP|SHOP00000000000",
            "MERCH0000000001|MERCH0000000001", "MERCH00000000001|MERCH0000000001",
            "MERCH10000000001|MERCH1000000000", "OTHER00000000001|OTHER0000000000",
            "OTHER000000000001|OTHER0000000000", "MERCH0000000000001|MERCH0000000000"},
            delimiter = '|', nullValues = "NULL")
    void shouldNormalizeMerchantWhenIdentifierHasDifferentFormats(String input, String expected) {
        // Act
        String actual = AuthorizationRequestNormalizer.normalizeMerchantId(input);
        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldPreserveTransactionDataWhenNormalizingRequest() {
        // Arrange
        AuthorizationRequest original = SwitchFixtures.request("4000001234567890");
        // Act
        AuthorizationRequest actual = AuthorizationRequestNormalizer.normalize(original);
        // Assert
        assertThat(actual).usingRecursiveComparison().ignoringFields("terminalId", "merchantId").isEqualTo(original);
        assertThat(actual.terminalId()).isEqualTo("TERM1000");
        assertThat(actual.merchantId()).isEqualTo("SHOP00000000000");
        assertThat(original.terminalId()).isEqualTo("TERM1");
    }
}
