package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.config.SwitchProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class SwitchFixtures {
    private SwitchFixtures() {
    }

    static AuthorizationRequest request(String pan) {
        return new AuthorizationRequest("0100", "000001", pan, "000000", new BigDecimal("1000"),
                "643", Instant.parse("2026-10-23T12:00:00Z"), "TERM1", "POS", "SHOP", "5411", "ACQ001", null);
    }

    static SwitchProperties properties() {
        return new SwitchProperties("1.0", Map.of("400000", "ISS001", "500000", "ISS002"),
                "http://authorization", "http://logger", "http://acquirer", null,
                new SwitchProperties.RetryProperties(1, List.of()), null);
    }
}
