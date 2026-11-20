package com.processing.apitests;

import io.restassured.response.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.*;

class CardApiTest {
    @Test
    void createsCardWithContractShapeAndContentType() {
        String request = ApiSupport.cardRequest("400000", ApiSupport.uniqueName(), "643", 1000);

        // given
        Response response = ApiSupport.request().body(request)
                // when: the public Gateway card endpoint is called
                .when().post("/api/cards");

        // then: status, header, schema and generated values are all contract assertions
        response.then().statusCode(201)
                .header("Content-Type", containsString("application/json"))
                .body(matchesJsonSchemaInClasspath("schemas/card-response.json"))
                .body("pan", matchesPattern("[0-9]{16}"))
                .body("status", equalTo("ACTIVE"))
                .body("availableBalance", equalTo(1000));
    }

    @ParameterizedTest(name = "invalid card request: {0}")
    @CsvSource({
            "wrong-bin,400000,643",
            "400000,abc,643",
            "400000,VALID NAME,64"
    })
    void rejectsOneInvalidEquivalenceClass(String bin, String name, String currency) {
        String request = ApiSupport.cardRequest(bin, name, currency, 1000);

        // given: exactly one invalid input class is changed
        ApiSupport.request().body(request)
                // when
                .when().post("/api/cards")
                // then: the validation contract rejects it with a structured error
                .then().statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    void listsCardsWithPaginationAndGPathSelection() {
        String pan = createCardAndGetPan();

        // given: a page-size boundary and an object created by this test
        ApiSupport.request().queryParam("limit", 500).queryParam("offset", 0)
                // when
                .when().get("/api/cards")
                // then: pagination and object-level selection are checked, not just HTTP 200
                .then().statusCode(200)
                .body("cards", hasSize(lessThanOrEqualTo(500)))
                .body("cards.findAll { it.pan == '" + pan + "' }", hasSize(1));
    }

    @Test
    void rejectsInvalidPaginationBoundary() {
        // given: limit=0 is outside @Positive on CardController
        ApiSupport.request().queryParam("limit", 0)
                // when / then
                .when().get("/api/cards").then().statusCode(400).body("message", notNullValue());
    }

    private String createCardAndGetPan() {
        return ApiSupport.request().body(ApiSupport.cardRequest(
                        "400000", ApiSupport.uniqueName(), "643", 1000))
                .when().post("/api/cards").then().statusCode(201)
                .extract().path("pan");
    }
}
