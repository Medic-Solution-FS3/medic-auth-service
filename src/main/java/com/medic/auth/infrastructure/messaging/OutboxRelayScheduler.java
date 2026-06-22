package com.medic.auth.infrastructure.messaging;

import com.medic.auth.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEvent;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final JpaOutboxEventRepository outboxRepo;
    private final RabbitTemplate rabbitTemplate;

    @Value("${outbox.relay.batch-size}")
    private int batchSize;

    @Value("${outbox.relay.max-retries}")
    private int maxRetries;

    public OutboxRelayScheduler(JpaOutboxEventRepository outboxRepo, RabbitTemplate rabbitTemplate) {
        this.outboxRepo = outboxRepo;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Polls the outbox table and forwards {@code PENDING} events to RabbitMQ.
     * <p>
     * Each event is attempted at-least-once: on publish failure the retry counter is incremented.
     * When {@code retryCount >= maxRetries} the event is moved to {@code FAILED} status and
     * no further delivery is attempted — operator intervention is required at that point.
     * The correlation ID from the originating HTTP request is propagated as an AMQP header
     * ({@code X-Correlation-Id}) to enable distributed tracing across services.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms}")
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepo.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        logger.info("Found {} pending outbox events to relay", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                rabbitTemplate.convertAndSend(
                        "user.exchange",
                        event.getRoutingKey(),
                        event.getPayload(),
                        message -> {
                            if (event.getCorrelationId() != null) {
                                message.getMessageProperties().setHeader("X-Correlation-Id", event.getCorrelationId());
                            }
                            return message;
                        }
                );

                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setProcessedAt(LocalDateTime.now());
                outboxRepo.save(event);

                logger.info("Published event {} with routing key {}", event.getId(), event.getRoutingKey());

            } catch (Exception e) {
                logger.error("Failed to publish event {}", event.getId(), e);

                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= maxRetries) {
                    event.setStatus(OutboxEventStatus.FAILED);
                    event.setFailureReason(e.getMessage());
                }
                outboxRepo.save(event);
            }
        }
    }
}
