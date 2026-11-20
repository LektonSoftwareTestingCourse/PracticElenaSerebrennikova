package com.processing.apitests;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

class SwitchApiTest {
    private static final String SWITCH = "http://localhost:8082";

    @Test
    void routesTransactionFromGatewayToAuthorization() {
        String pan = createCard();
        String request = "{\"mti\":\"0100\",\"stan\":\"123456\",\"pan\":\"" + pan
                + "\",\"processingCode\":\"000000\",\"amount\":1,\"currencyCode\":\"643\","
                + "\"transmissionDateTime\":\"2026-06-01T10:30:00Z\",\"terminalId\":\"TERM0001\","
                + "\"terminalType\":\"POS\",\"merchantId\":\"MERCH0000000001\","
                + "\"mcc\":\"5411\",\"acquirerId\":\"ACQ001\"}";

        ApiSupport.requestAt(SWITCH).body(request).when().post("/api/internal/route")
                .then().statusCode(200)
                .body("status", oneOf("APPROVED", "DECLINED"))
                .body("stan", equalTo("123456"));
    }

    @Test
    void rejectsMalformedRoutePayload() {
        ApiSupport.requestAt(SWITCH).body("{\"pan\":\"bad\"}")
                .when().post("/api/internal/route")
                .then().statusCode(anyOf(is(400), is(500)));
    }

    private String createCard() {
        return ApiSupport.request().body(ApiSupport.cardRequest(
                        "400000", ApiSupport.uniqueName(), "643", 1_000_000))
                .when().post("/api/cards").then().statusCode(201).extract().path("pan");
    }
}