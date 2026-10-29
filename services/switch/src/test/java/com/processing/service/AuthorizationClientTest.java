package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationClientTest {
    @Mock private RestClient rest;
    @Mock private RestClient.RequestBodyUriSpec post;
    @Mock private RestClient.RequestBodySpec body;
    @Mock private RestClient.ResponseSpec response;
    private CircuitBreaker breaker;
    private AuthorizationClient client;

    @BeforeEach
    void setUp() {
        breaker = CircuitBreaker.ofDefaults("test");
        Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).build());
        client = new AuthorizationClient(SwitchFixtures.properties(), rest, retry, breaker);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "DECLINED"})
    void shouldReturnAuthorizationWhenResponseStatusIsValid(String status) {
        // Arrange
        stubPost();
        AuthorizationResponse expected = authorizationResponse(status);
        when(response.body(AuthorizationResponse.class)).thenReturn(expected);
        // Act
        AuthorizationResponse actual = client.authorize(SwitchFixtures.request("4000001234567890"));
        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PENDING", ""})
    void shouldRejectAuthorizationWhenResponseIsMissingOrInvalid(String status) {
        // Arrange
        stubPost();
        when(response.body(AuthorizationResponse.class))
                .thenReturn(status == null ? null : authorizationResponse(status));
        // Act / Assert
        assertThatThrownBy(() -> client.authorize(SwitchFixtures.request("4000001234567890")))
                .isInstanceOf(AuthorizationException.class).hasMessageContaining("Invalid response");
    }

    @Test
    void shouldRejectAuthorizationWhenCircuitBreakerIsOpen() {
        // Arrange
        breaker.transitionToOpenState();
        // Act / Assert
        assertThatThrownBy(() -> client.authorize(SwitchFixtures.request("4000001234567890")))
                .isInstanceOf(AuthorizationException.class).hasMessageContaining("Circuit breaker open");
    }

    @Test
    void shouldReportRootCauseWhenTransportFails() {
        // Arrange
        when(rest.post()).thenThrow(new IllegalStateException("wrapper", new IllegalArgumentException("connection lost")));
        // Act / Assert
        assertThatThrownBy(() -> client.authorize(SwitchFixtures.request("4000001234567890")))
                .isInstanceOf(AuthorizationException.class).hasMessageContaining("connection lost");
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "DECLINED"})
    void shouldReturnRollbackWhenResponseStatusIsValid(String status) {
        // Arrange
        stubPost();
        RollbackResponse expected = new RollbackResponse("123456789012", "00", status, null, 0);
        when(response.body(RollbackResponse.class)).thenReturn(expected);
        // Act
        RollbackResponse actual = client.rollback(SwitchFixtures.request("4000001234567890"), "123456789012");
        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PENDING", ""})
    void shouldReturnNullWhenRollbackResponseIsMissingOrInvalid(String status) {
        // Arrange
        stubPost();
        when(response.body(RollbackResponse.class))
                .thenReturn(status == null ? null : new RollbackResponse("123456789012", "05", status, null, 0));
        // Act
        RollbackResponse actual = client.rollback(SwitchFixtures.request("4000001234567890"), "123456789012");
        // Assert
        assertThat(actual).isNull();
    }

    private void stubPost() {
        when(rest.post()).thenReturn(post);
        when(post.uri(anyString())).thenReturn(body);
        when(body.body(any(Object.class))).thenReturn(body);
        when(body.retrieve()).thenReturn(response);
        when(response.onStatus(any(), any())).thenReturn(response);
    }

    private static AuthorizationResponse authorizationResponse(String status) {
        return new AuthorizationResponse("0110", "000001", "123456789012", "AUTH01", "00", status, null, 0);
    }
}
