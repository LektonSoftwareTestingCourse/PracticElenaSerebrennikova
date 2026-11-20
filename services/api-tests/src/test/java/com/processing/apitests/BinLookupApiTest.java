package com.processing.apitests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.Matchers.*;

class BinLookupApiTest {
    private static final String BIN_LOOKUP = "http://localhost:8096";

    @ParameterizedTest(name = "known BIN: {0}")
    @ValueSource(strings = {"400000", "400001", "400004"})
    void returnsIssuerForKnownBin(String bin) {
        ApiSupport.requestAt(BIN_LOOKUP)
                // when
                .when().get("/api/bin/{bin}", bin)
                // then: all observable response fields are checked
                .then().statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("bin", equalTo(bin))
                .body("issuerId", matchesPattern("ISS[0-9]{3}"))
                .body("issuerName", not(isEmptyOrNullString()));
    }

    @Test
    void returnsNotFoundForUnknownBin() {
        // given: 499999 is outside the service lookup table
        ApiSupport.requestAt(BIN_LOOKUP).when().get("/api/bin/499999")
                .then().statusCode(404).body(emptyString());
    }

    @Test
    void failureFlagProducesServerError() {
        // The controller explicitly documents fail=true as a deterministic failure hook.
        ApiSupport.requestAt(BIN_LOOKUP).queryParam("fail", true)
                .when().get("/api/bin/400000")
                .then().statusCode(500);
    }
}
