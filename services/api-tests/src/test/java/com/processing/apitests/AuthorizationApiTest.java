package com.processing.apitests;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.*;

class AuthorizationApiTest {
    private static final String AUTHORIZATION = "http://localhost:8083";

    @Test
    void approvesTransactionForNewActiveCard() {
        String pan = createCard();
        String stan = uniqueStan();

        ApiSupport.requestAt(AUTHORIZATION).body(authorizationRequest(pan, stan, "1"))
                .when().post("/api/internal/authorize")
                .then().statusCode(200)
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"))
                .body("stan", equalTo(stan))
                .body("rrn", matchesPattern("[0-9]{12}"))
                .body("authCode", notNullValue());
    }

    @Test
    void declinesUnknownCardWithDomainCode() {
        ApiSupport.requestAt(AUTHORIZATION)
                .body(authorizationRequest("4000009999999999", uniqueStan(), "1"))
                .when().post("/api/internal/authorize")
                .then().statusCode(404)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("14"))
                .body("declineReason", notNullValue());
    }

    @Test
    void rejectsMalformedAuthorizationRequest() {
        ApiSupport.requestAt(AUTHORIZATION).body("{\"mti\":\"0100\"}")
                .when().post("/api/internal/authorize")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void rejectsMalformedRollbackRequest() {
        ApiSupport.requestAt(AUTHORIZATION).body("{\"rrn\":\"bad\"}")
                .when().post("/api/internal/rollback")
                .then().statusCode(400).body("message", notNullValue());
    }

    private String createCard() {
        return ApiSupport.request().body(ApiSupport.cardRequest(
                        "400000", ApiSupport.uniqueName(), "643", 1_000_000))
                .when().post("/api/cards").then().statusCode(201).extract().path("pan");
    }

    private String authorizationRequest(String pan, String stan, String amount) {
        return "{"
                + "\"mti\":\"0100\",\"stan\":\"" + stan + "\",\"pan\":\"" + pan + "\","
                + "\"processingCode\":\"000000\",\"amount\":" + amount + ","
                + "\"currencyCode\":\"643\",\"transmissionDateTime\":\"2026-06-01T10:30:00Z\","
                + "\"terminalId\":\"TERM0001\",\"terminalType\":\"POS\","
                + "\"merchantId\":\"MERCH0000000001\",\"mcc\":\"5411\","
                + "\"acquirerId\":\"ACQ001\",\"issuerId\":\"ISS001\"}";
    }

    private String uniqueStan() {
        return String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);
    }
}