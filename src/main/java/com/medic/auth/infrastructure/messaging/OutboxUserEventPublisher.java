package com.medic.auth.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medic.auth.domain.model.User;
import com.medic.auth.infrastructure.messaging.event.EmailVerifiedEvent;
import com.medic.auth.infrastructure.messaging.event.PasswordResetRequestedEvent;
import com.medic.auth.infrastructure.messaging.event.UserRegisteredEvent;
import com.medic.auth.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEvent;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxUserEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxUserEventPublisher.class);

    private final JpaOutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public OutboxUserEventPublisher(JpaOutboxEventRepository outboxRepo, ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publishUserRegistered(User user, String verificationToken) {
        UserRegisteredEvent event = UserRegisteredEvent.from(user, verificationToken);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(user.getId());
        outboxEvent.setEventType("UserRegistered");
        outboxEvent.setRoutingKey("user.registered");
        outboxEvent.setPayload(serializeEvent(event));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);
        outboxEvent.setCorrelationId(getCorrelationId());

        outboxRepo.save(outboxEvent);
        logger.info("UserRegistered event saved to outbox for userId: {}", user.getId());
    }

    @Transactional
    public void publishEmailVerified(User user) {
        EmailVerifiedEvent event = EmailVerifiedEvent.from(user);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(user.getId());
        outboxEvent.setEventType("EmailVerified");
        outboxEvent.setRoutingKey("user.email.verified");
        outboxEvent.setPayload(serializeEvent(event));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);
        outboxEvent.setCorrelationId(getCorrelationId());

        outboxRepo.save(outboxEvent);
        logger.info("EmailVerified event saved to outbox for userId: {}", user.getId());
    }

    @Transactional
    public void publishPasswordResetRequested(User user, String resetToken) {
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.from(user, resetToken);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(user.getId());
        outboxEvent.setEventType("PasswordResetRequested");
        outboxEvent.setRoutingKey("user.password.reset.requested");
        outboxEvent.setPayload(serializeEvent(event));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);
        outboxEvent.setCorrelationId(getCorrelationId());

        outboxRepo.save(outboxEvent);
        logger.info("PasswordResetRequested event saved to outbox for userId: {}", user.getId());
    }

    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    private String getCorrelationId() {
        return MDC.get("correlationId");
    }
}
