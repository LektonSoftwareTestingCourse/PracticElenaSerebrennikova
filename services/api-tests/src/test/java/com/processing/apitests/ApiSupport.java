package com.processing.apitests;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.UUID;

import static io.restassured.RestAssured.given;

final class ApiSupport {
    static final String GATEWAY = System.getProperty("gateway.url", "http://localhost:8080");
    static final RequestSpecification JSON_REQUEST = new RequestSpecBuilder()
            .setBaseUri(GATEWAY)
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .build();

    private ApiSupport() {
    }

    static RequestSpecification request() {
        return given().spec(JSON_REQUEST);
    }

    static RequestSpecification requestAt(String baseUri) {
        return given().spec(JSON_REQUEST).baseUri(baseUri);
    }

    static String cardRequest(String bin, String name, String currency, int balance) {
        return "{" +
                "\"bin\":\"" + bin + "\"," +
                "\"cardholderName\":\"" + name + "\"," +
                "\"currencyCode\":\"" + currency + "\"," +
                "\"dailyLimit\":100000," +
                "\"monthlyLimit\":300000," +
                "\"initialBalance\":" + balance + "}";
    }

    static String uniqueName() {
        return "API TEST " + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
