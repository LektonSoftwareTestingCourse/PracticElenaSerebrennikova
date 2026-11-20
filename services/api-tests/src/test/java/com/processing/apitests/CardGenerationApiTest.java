package com.processing.apitests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.Matchers.*;

class CardGenerationApiTest {
    @ParameterizedTest(name = "generate count: {0}")
    @ValueSource(ints = {1, 2})
    void generatesRequestedNumberOfCards(int count) {
        String body = "{\"count\":" + count + ",\"bins\":[\"400000\"]}";

        // given / when / then
        ApiSupport.request().body(body).when().post("/api/cards/generate")
                .then().statusCode(201)
                .body("count", equalTo(count))
                .body("cards", hasSize(count))
                .body("cards.pan", everyItem(matchesPattern("[0-9]{16}")))
                .body("cards.status", everyItem(equalTo("ACTIVE")));
    }

    @Test
    void rejectsZeroCards() {
        // @Min(1) on GenerateCardsRequest defines zero as an invalid boundary.
        ApiSupport.request().body("{\"count\":0,\"bins\":[\"400000\"]}")
                .when().post("/api/cards/generate")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void rejectsEmptyBinCollection() {
        // @NotEmpty on GenerateCardsRequest is a separate invalid class.
        ApiSupport.request().body("{\"count\":1,\"bins\":[]}")
                .when().post("/api/cards/generate")
                .then().statusCode(400).body("message", notNullValue());
    }
}