package com.processing.apitests;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

class CardManagementLifecycleApiTest {
    @Test
    void patchesCardLimitsAndReturnsUpdatedCard() {
        String pan = createCard();
        String body = "{\"dailyLimit\":200000,\"monthlyLimit\":500000}";

        ApiSupport.request().body(body).when().patch("/api/cards/{pan}", pan)
                .then().statusCode(200)
                .body("pan", equalTo(pan))
                .body("dailyLimit", equalTo(200000))
                .body("monthlyLimit", equalTo(500000));
    }

    @Test
    void getsCardByPanWithFullContractBody() {
        String pan = createCard();
        ApiSupport.request().when().get("/api/cards/{pan}", pan)
                .then().statusCode(200)
                .body("pan", equalTo(pan))
                .body("bin", equalTo("400000"))
                .body("status", equalTo("ACTIVE"))
                .body("currencyCode", equalTo("643"));
    }

    @Test
    void rejectsNegativePatchLimit() {
        String pan = createCard();
        ApiSupport.request().body("{\"dailyLimit\":-1}")
                .when().patch("/api/cards/{pan}", pan)
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void reservesAndRollsBackFunds() {
        String pan = createCard();
        String rrn = "123456789012";
        String operation = "{\"amount\":100,\"rrn\":\"" + rrn + "\"}";

        ApiSupport.request().body(operation).when().post("/api/cards/{pan}/reserve", pan)
                .then().statusCode(200).body("pan", equalTo(pan));
        ApiSupport.request().body(operation).when().post("/api/cards/{pan}/rollback", pan)
                .then().statusCode(200).body("pan", equalTo(pan));
    }

    @Test
    void rejectsInvalidReserveAmount() {
        String pan = createCard();
        ApiSupport.request().body("{\"amount\":0,\"rrn\":\"123456789012\"}")
                .when().post("/api/cards/{pan}/reserve", pan)
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void deletesCardWithEmptyNoContentResponse() {
        String pan = createCard();
        ApiSupport.request().when().delete("/api/cards/{pan}", pan)
                .then().statusCode(204).body(emptyString());
    }

    @Test
    void bulkUpdateReturnsNumberOfAffectedCards() {
        String pan = createCard();
        String body = "{\"pans\":[\"" + pan + "\"],\"status\":\"BLOCKED\"}";
        ApiSupport.request().body(body).when().post("/api/cards/bulk-update")
                .then().statusCode(200).body("updated", greaterThanOrEqualTo(0));
    }

    private String createCard() {
        return ApiSupport.request().body(ApiSupport.cardRequest(
                        "400000", ApiSupport.uniqueName(), "643", 1_000_000))
                .when().post("/api/cards").then().statusCode(201).extract().path("pan");
    }
}
