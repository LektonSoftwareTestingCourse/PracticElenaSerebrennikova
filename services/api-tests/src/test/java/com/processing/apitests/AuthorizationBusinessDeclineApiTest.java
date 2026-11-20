package com.processing.apitests;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;


class AuthorizationBusinessDeclineApiTest {
    private static final String AUTHORIZATION = "http://localhost:8083";

    @Test
    void declinesBlockedCardWithExactReason() {
        String pan = createCard(1_000, 10_000, 1_000);
        patchStatus(pan, "BLOCKED");
        assertDecline(pan, "05", "CARD_BLOCKED", 403, "1");
    }

    @Test
    void declinesInactiveCardWithExactReason() {
        String pan = createCard(1_000, 10_000, 1_000);
        patchStatus(pan, "INACTIVE");
        assertDecline(pan, "05", "CARD_INACTIVE", 403, "1");
    }

    @Test
    void declinesExpiredStatusWithExactReason() {
        String pan = createCard(1_000, 10_000, 1_000);
        patchStatus(pan, "EXPIRED");
        assertDecline(pan, "54", "CARD_EXPIRED", 403, "1");
    }

    @Test
    void declinesActiveCardWhenTransactionDateIsAfterExpiry() {
        String pan = createCard(1_000, 10_000, 1_000);
        String futureDate = Instant.now().plus(4, ChronoUnit.YEARS).toString();
        assertDecline(pan, "54", "CARD_EXPIRED", 403, "1", futureDate);
    }

    @Test
    void declinesWhenDailyLimitIsExceeded() {
        String pan = createCard(100, 10_000, 1_000);
        assertDecline(pan, "61", "EXCEEDS_AMOUNT_LIMIT", 400, "101");
    }

    @Test
    void declinesWhenMonthlyLimitIsExceeded() {
        String pan = createCard(10_000, 100, 1_000);
        assertDecline(pan, "61", "EXCEEDS_AMOUNT_LIMIT", 400, "101");
    }

    @Test
    void declinesWhenAvailableBalanceIsInsufficient() {
        String pan = createCard(10_000, 100_000, 10);
        assertDecline(pan, "51", "INSUFFICIENT_FUNDS", 422, "11");
    }

    @Test
    void declinesUnknownCardWithExactReason() {
        assertDecline("4000001234560001", "14", "CARD_NOT_FOUND", 404, "1");
    }

    private void assertDecline(String pan, String code, String reason,
                               int httpStatus, String amount) {
        assertDecline(pan, code, reason, httpStatus, amount, "2026-06-01T10:30:00Z");
    }

    private void assertDecline(String pan, String code, String reason,
                               int httpStatus, String amount, String transmissionDateTime) {
        ApiSupport.requestAt(AUTHORIZATION)
                .body(authorizationRequest(pan, uniqueStan(), amount, transmissionDateTime))
                .when().post("/api/internal/authorize")
                .then().statusCode(httpStatus)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo(code))
                .body("declineReason", equalTo(reason))
                .body("processingTimeMs", notNullValue());
    }

    private String createCard(int dailyLimit, int monthlyLimit, int balance) {
        String body = "{"
                + "\"bin\":\"400000\",\"cardholderName\":\"" + ApiSupport.uniqueName() + "\","
                + "\"currencyCode\":\"643\",\"dailyLimit\":" + dailyLimit + ","
                + "\"monthlyLimit\":" + monthlyLimit + ",\"initialBalance\":" + balance + "}";
        return ApiSupport.request().body(body).when().post("/api/cards")
                .then().statusCode(201).extract().path("pan");
    }

    private void patchStatus(String pan, String status) {
        ApiSupport.request().body("{\"status\":\"" + status + "\"}")
                .when().patch("/api/cards/{pan}", pan).then().statusCode(200);
    }

    private String authorizationRequest(String pan, String stan, String amount, String date) {
        return "{"
                + "\"mti\":\"0100\",\"stan\":\"" + stan + "\",\"pan\":\"" + pan + "\","
                + "\"processingCode\":\"000000\",\"amount\":" + amount + ","
                + "\"currencyCode\":\"643\",\"transmissionDateTime\":\"" + date + "\","
                + "\"terminalId\":\"TERM0001\",\"terminalType\":\"POS\","
                + "\"merchantId\":\"MERCH0000000001\",\"mcc\":\"5411\","
                + "\"acquirerId\":\"ACQ001\",\"issuerId\":\"ISS001\"}";
    }

    private String uniqueStan() {
        return String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);
    }
}
