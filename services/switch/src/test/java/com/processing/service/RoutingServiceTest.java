package com.processing.service;

import com.processing.exception.UnknownBinException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceTest {
    private final RoutingService service = new RoutingService(SwitchFixtures.properties());

    @ParameterizedTest
    @CsvSource({"400000, ISS001", "4000001234567890, ISS001", "5000001234567890, ISS002"})
    void shouldReturnIssuerWhenBinIsKnown(String pan, String issuer) {
        // Act
        String actual = service.getIssuerIdByPan(pan);
        // Assert
        assertThat(actual).isEqualTo(issuer);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"40000", "999999", "9999991234567890"})
    void shouldRejectWhenPanIsShortOrBinIsUnknown(String pan) {
        // Act / Assert
        assertThatThrownBy(() -> service.getIssuerIdByPan(pan)).isInstanceOf(UnknownBinException.class);
    }
}
