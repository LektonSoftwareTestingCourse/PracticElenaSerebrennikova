package com.processing.apitests;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.Matchers.*;

class SimulatorApiTest {
    @ParameterizedTest(name = "invalid terminal count: {0}")
    @ValueSource(ints = {0, -1})
    void terminalSimulatorRejectsNonPositiveCount(int count) {
        String body = "{\"count\":" + count + ",\"scenario\":\"normal\",\"tps\":1}";

        ApiSupport.request().body(body).when().post("/api/simulator/terminal/run")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void terminalSimulatorRejectsMissingScenario() {
        ApiSupport.request().body("{\"count\":1,\"tps\":1}")
                .when().post("/api/simulator/terminal/run")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void merchantSimulatorRejectsInvalidCount() {
        ApiSupport.request().body("{\"count\":0,\"scenario\":\"grocery\"}")
                .when().post("/api/simulator/merchant/run")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void merchantListReturnsAnArray() {
        ApiSupport.request().when().get("/api/simulator/merchants")
                .then().statusCode(200).body("", isA(List.class));
    }

    @Test
    void terminalStopEndpointIsIdempotentlyReachable() {
        ApiSupport.request().when().post("/api/simulator/terminal/stop")
                .then().statusCode(200).body(emptyString());
    }

    @Test
    void terminalContinuousModeRejectsNonPositiveTps() {
        ApiSupport.request().body("{\"tps\":0,\"transactionType\":\"NORMAL\"}")
                .when().post("/api/simulator/terminal/start-continuous")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    void merchantFeeEndpointRejectsMalformedRequest() {
        ApiSupport.request().body("{}")
                .when().post("/api/simulator/merchant/fee")
                .then().statusCode(400).body("message", notNullValue());
    }
}