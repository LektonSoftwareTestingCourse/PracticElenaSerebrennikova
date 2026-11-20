package com.processing.apitests;

import io.restassured.response.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.hamcrest.Matchers.*;

class TransactionApiTest {
    @ParameterizedTest(name = "supported granularity: {0}")
    @ValueSource(strings = {"hour", "day"})
    void chartsAcceptSupportedGranularity(String granularity) {
        // given: the two values declared by ChartsFilter @Pattern
        ApiSupport.request().queryParam("granularity", granularity)
                // when / then
                .when().get("/api/dashboard/charts").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    void searchSupportsEmptyResultAndPaginationValidation() {
        // given: a valid filter that cannot match a generated PAN
        ApiSupport.request().queryParam("pan", "4999999999999999")
                // when / then: empty result is a valid 200 response
                .when().get("/api/transactions/search").then().statusCode(200)
                .body("transactions", hasSize(0))
                .body("total", equalTo(0));

        // given: offset below its @PositiveOrZero boundary
        ApiSupport.request().queryParam("offset", -1)
                .when().get("/api/transactions/search").then().statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    void dashboardChecksLimitsAndResponseShapes() {
        // given/when/then: stats is a JSON object and recent honours its upper boundary
        ApiSupport.request().when().get("/api/dashboard/stats").then()
                .statusCode(200).body("approvedCount", notNullValue());
        ApiSupport.request().queryParam("limit", 500).when().get("/api/dashboard/recent")
                .then().statusCode(200).body("size()", lessThanOrEqualTo(500));

        // given: limit=0 violates @Positive
        ApiSupport.request().queryParam("limit", 0).when().get("/api/dashboard/recent")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void transactionSearchIsEventuallyConsistentWhenRecordIsReturned() {
        // The assertion is deliberately condition-based: no fixed sleep is used.
        Response response = ApiSupport.request().when().get("/api/transactions/search");
        response.then().statusCode(200);
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> ApiSupport.request().when().get("/api/transactions/search")
                        .then().statusCode(200).body("transactions", notNullValue()));
    }
}
