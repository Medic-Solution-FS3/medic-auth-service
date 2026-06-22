package com.medic.auth.infrastructure.messaging;

import com.medic.auth.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEvent;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.amqp.core.MessagePostProcessor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private JpaOutboxEventRepository outboxRepo;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxRelayScheduler(outboxRepo, rabbitTemplate);
        ReflectionTestUtils.setField(scheduler, "batchSize", 100);
        ReflectionTestUtils.setField(scheduler, "maxRetries", 3);
    }

    @Test
    void relayPendingEvents_ShouldPublishEvents_WhenPendingEventsExist() {
        // Given
        OutboxEvent event1 = createOutboxEvent(1L, "user.registered");
        OutboxEvent event2 = createOutboxEvent(2L, "user.email.verified");

        List<OutboxEvent> pendingEvents = Arrays.asList(event1, event2);
        when(outboxRepo.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(pendingEvents);

        when(outboxRepo.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        scheduler.relayPendingEvents();

        // Then
        verify(rabbitTemplate, times(2)).convertAndSend(
                eq("user.exchange"),
                anyString(),
                anyString(),
                any(MessagePostProcessor.class)
        );

        verify(outboxRepo, times(2)).save(argThat(event ->
                event.getStatus() == OutboxEventStatus.PUBLISHED &&
                event.getProcessedAt() != null
        ));
    }

    @Test
    void relayPendingEvents_ShouldMarkAsFailed_WhenMaxRetriesExceeded() {
        // Given
        OutboxEvent event = createOutboxEvent(1L, "user.registered");
        event.setRetryCount(2); // Already 2 retries

        when(outboxRepo.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(Collections.singletonList(event));

        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString(), any(MessagePostProcessor.class));

        when(outboxRepo.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        scheduler.relayPendingEvents();

        // Then
        verify(outboxRepo).save(argThat(savedEvent ->
                savedEvent.getStatus() == OutboxEventStatus.FAILED &&
                savedEvent.getRetryCount() == 3 &&
                savedEvent.getFailureReason() != null
        ));
    }

    @Test
    void relayPendingEvents_ShouldDoNothing_WhenNoPendingEvents() {
        // Given
        when(outboxRepo.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(Collections.emptyList());

        // When
        scheduler.relayPendingEvents();

        // Then
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString(), any(MessagePostProcessor.class));
        verify(outboxRepo, never()).save(any());
    }

    private OutboxEvent createOutboxEvent(Long id, String routingKey) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateId(1L);
        event.setEventType("UserRegistered");
        event.setRoutingKey(routingKey);
        event.setPayload("{\"version\":\"v1\"}");
        event.setStatus(OutboxEventStatus.PENDING);
        event.setRetryCount(0);
        return event;
    }
}
