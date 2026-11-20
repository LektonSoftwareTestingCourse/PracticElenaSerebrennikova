package com.processing.apitests;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.Matchers.*;

class HealthApiTest {
    @ParameterizedTest(name = "{0} health")
    @CsvSource({
            "gateway,http://localhost:8080/health",
            "card-management,http://localhost:8081/health",
            "switch,http://localhost:8082/health",
            "authorization,http://localhost:8083/health",
            "terminal,http://localhost:8085/health",
            "merchant,http://localhost:8084/health",
            "logger,http://localhost:8088/health"
    })
    void serviceHealthIsAvailable(String service, String endpoint) {
        ApiSupport.requestAt(endpoint.substring(0, endpoint.indexOf("/health")))
                .when().get("/health").then().statusCode(200)
                .body("status", anyOf(equalTo("ok"), equalTo("UP"), notNullValue()));
    }

    @org.junit.jupiter.api.Test
    void binLookupActuatorHealthIsAvailable() {
        ApiSupport.requestAt("http://localhost:8096").when().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
    }

    @org.junit.jupiter.api.Test
    void notificationActuatorHealthIsAvailable() {
        ApiSupport.requestAt("http://localhost:8097").when().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
    }
}
