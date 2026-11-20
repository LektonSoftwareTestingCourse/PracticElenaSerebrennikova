package com.processing.apitests;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

/** Contract tests for Transaction Logger public and internal endpoints. */
class LoggerApiTest {
    private static final String LOGGER = "http://localhost:8088";

    @Test
    void exportsTransactionsAsCsvWithDownloadHeader() {
        ApiSupport.requestAt(LOGGER).when().get("/api/transactions/export")
                .then().statusCode(200)
                .header("Content-Type", containsString("text/csv"))
                .header("Content-Disposition", containsString("transactions.csv"));
    }

    @Test
    void rejectsInvalidSearchPagination() {
        ApiSupport.requestAt(LOGGER).queryParam("limit", 0)
                .when().get("/api/transactions/search")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void rejectsMalformedInternalTransactionPayload() {
        ApiSupport.requestAt(LOGGER).body("{}")
                .when().post("/api/internal/log")
                .then().statusCode(400).body("message", notNullValue());
    }
}