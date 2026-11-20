package com.processing.apitests;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

class BinManagementApiTest {
    private static final String CARD_MANAGEMENT = "http://localhost:8081";

    @Test
    void listsBinMappings() {
        ApiSupport.requestAt(CARD_MANAGEMENT).when().get("/api/bins")
                .then().statusCode(200).body("", isA(java.util.List.class));
    }

    @Test
    void createsReadsAndDeletesBinMapping() {
        String bin = "499999";
        String body = "{\"bin\":\"" + bin + "\",\"issuerId\":\"ISS999\"}";

        ApiSupport.requestAt(CARD_MANAGEMENT).body(body).when().post("/api/bins").then()
                .statusCode(201).body("bin", equalTo(bin)).body("issuerId", equalTo("ISS999"));
        ApiSupport.requestAt(CARD_MANAGEMENT).when().get("/api/bins/{bin}", bin).then()
                .statusCode(200).body("issuerId", equalTo("ISS999"));
        ApiSupport.requestAt(CARD_MANAGEMENT).when().delete("/api/bins/{bin}", bin).then()
                .statusCode(204).body(emptyString());
    }

    @Test
    void rejectsInvalidBinMapping() {
        ApiSupport.requestAt(CARD_MANAGEMENT).body("{\"bin\":\"12\",\"issuerId\":\"\"}")
                .when().post("/api/bins").then().statusCode(400).body("message", notNullValue());
    }
}