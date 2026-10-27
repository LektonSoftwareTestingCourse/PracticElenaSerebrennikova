package com.processing.service;

import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggerClientTest {
    @Mock private RabbitTemplate rabbit;
    private LoggerClient client;

    @BeforeEach
    void setUp() {
        client = new LoggerClient(rabbit);
    }

    @Test
    void shouldPublishToTransactionExchangeWhenBrokerIsAvailable() {
        // Arrange
        TransactionRequest transaction = mock(TransactionRequest.class);
        // Act
        boolean actual = client.log(transaction);
        // Assert
        assertThat(actual).isTrue();
        verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, transaction);
    }

    @Test
    void shouldReturnFalseWhenBrokerRejectsPublication() {
        // Arrange
        TransactionRequest transaction = mock(TransactionRequest.class);
        doThrow(new AmqpException("broker down")).when(rabbit)
                .convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, transaction);
        // Act
        boolean actual = client.log(transaction);
        // Assert
        assertThat(actual).isFalse();
    }
}
