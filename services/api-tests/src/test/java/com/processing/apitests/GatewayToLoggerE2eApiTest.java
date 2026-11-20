package com.processing.apitests;

import io.restassured.response.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class GatewayToLoggerE2eApiTest {
    @Test
    void transactionSentThroughGatewayAppearsExactlyOnceInLogger() {
        String pan = createCard();
        String stan = uniqueStan();
        String request = transactionRequest(pan, stan, "1");

        // given/when: send one transaction through the public entry point
        Response transaction = ApiSupport.request().body(request)
                .when().post("/api/transactions");

        // then: Gateway returns a domain decision
        transaction.then().statusCode(200).body("status", notNullValue());
        String status = transaction.jsonPath().getString("status");

        // and: the asynchronous Logger effect appears, correlated by unique STAN
        Awaitility.await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> ApiSupport.request()
                        .queryParam("stan", stan)
                        .when().get("/api/transactions/search")
                        .then().statusCode(200)
                        .body("total", equalTo(1))
                        .body("transactions", hasSize(1))
                        .body("transactions[0].stan", equalTo(stan))
                        .body("transactions[0].status", equalTo(status)));
    }

    @Test
    void declinedTransactionDoesNotChangeCardBalanceAndIsLogged() {
        String pan = createCardWithBalance(1);
        int balanceBefore = ApiSupport.request().when().get("/api/cards/{pan}", pan)
                .then().statusCode(200).extract().path("availableBalance");
        String stan = uniqueStan();

        // amount exceeds balance
        ApiSupport.request().body(transactionRequest(pan, stan, "100"))
                .when().post("/api/transactions").then().statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("51"))
                .body("declineReason", equalTo("INSUFFICIENT_FUNDS"));

        // absence of a reservation effect is checked through the card API
        ApiSupport.request().when().get("/api/cards/{pan}", pan)
                .then().statusCode(200).body("availableBalance", equalTo(balanceBefore));

        // the declined decision itself still reaches Logger exactly once
        Awaitility.await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> ApiSupport.request().queryParam("stan", stan)
                        .when().get("/api/transactions/search")
                        .then().statusCode(200).body("total", equalTo(1))
                        .body("transactions[0].status", equalTo("DECLINED")));
    }

    private String createCard() {
        return createCardWithBalance(1_000_000);
    }

    private String createCardWithBalance(int balance) {
        return ApiSupport.request().body(ApiSupport.cardRequest(
                        "400000", ApiSupport.uniqueName(), "643", balance))
                .when().post("/api/cards").then().statusCode(201).extract().path("pan");
    }

    private String transactionRequest(String pan, String stan, String amount) {
        return "{"
                + "\"mti\":\"0100\",\"stan\":\"" + stan + "\",\"pan\":\"" + pan + "\","
                + "\"processingCode\":\"000000\",\"amount\":" + amount + ","
                + "\"currencyCode\":\"643\",\"transmissionDateTime\":\"2026-06-01T10:30:00Z\","
                + "\"terminalId\":\"TERM0001\",\"terminalType\":\"POS\","
                + "\"merchantId\":\"MERCH0000000001\",\"mcc\":\"5411\",\"acquirerId\":\"ACQ001\"}";
    }

    private String uniqueStan() {
        return String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);
    }
}
