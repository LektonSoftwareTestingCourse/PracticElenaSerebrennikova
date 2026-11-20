package com.processing.apitests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.Matchers.*;

class NotificationApiTest {
    private static final String NOTIFICATIONS = "http://localhost:8097";

    @ParameterizedTest(name = "page={0}, size={1}")
    @CsvSource({"0,1", "0,50", "1,10"})
    void returnsSpringPageForValidPagination(int page, int size) {
        ApiSupport.requestAt(NOTIFICATIONS).queryParam("page", page).queryParam("size", size)
                .when().get("/api/notifications")
                .then().statusCode(200)
                .body("content", notNullValue())
                .body("number", equalTo(page))
                .body("size", equalTo(size));
    }

    @Test
    void rejectsNegativePage() {
        // PageRequest cannot represent a negative zero-based page.
        ApiSupport.requestAt(NOTIFICATIONS).queryParam("page", -1)
                .when().get("/api/notifications")
                .then().statusCode(anyOf(is(400), is(500)));
    }
}
