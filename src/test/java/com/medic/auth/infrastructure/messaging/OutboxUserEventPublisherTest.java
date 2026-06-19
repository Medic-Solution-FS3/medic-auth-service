package com.medic.auth.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medic.auth.domain.model.Role;
import com.medic.auth.domain.model.User;
import com.medic.auth.domain.model.UserRole;
import com.medic.auth.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEvent;
import com.medic.auth.infrastructure.persistence.outbox.OutboxEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxUserEventPublisherTest {

    @Mock
    private JpaOutboxEventRepository outboxRepo;

    private OutboxUserEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxUserEventPublisher(outboxRepo, new ObjectMapper());
    }

    private User createUser() {
        Role role = new Role(UserRole.PACIENTE);
        role.setId(1L);
        User user = new User();
        user.setId(10L);
        user.setEmail("test@test.com");
        user.setFullName("Test User");
        user.setPhone("+56912345678");
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(false);
        return user;
    }

    @Test
    void publishUserRegistered_ShouldSavePendingOutboxEvent() {
        User user = createUser();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        publisher.publishUserRegistered(user, "verification-token-123");

        verify(outboxRepo).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals(10L, saved.getAggregateId());
        assertEquals("UserRegistered", saved.getEventType());
        assertEquals("user.registered", saved.getRoutingKey());
        assertEquals(OutboxEventStatus.PENDING, saved.getStatus());
        assertNotNull(saved.getPayload());
        assertTrue(saved.getPayload().contains("verification-token-123"));
    }

    @Test
    void publishEmailVerified_ShouldSavePendingOutboxEvent() {
        User user = createUser();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        publisher.publishEmailVerified(user);

        verify(outboxRepo).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals(10L, saved.getAggregateId());
        assertEquals("EmailVerified", saved.getEventType());
        assertEquals("user.email.verified", saved.getRoutingKey());
        assertEquals(OutboxEventStatus.PENDING, saved.getStatus());
        assertNotNull(saved.getPayload());
    }

    @Test
    void publishPasswordResetRequested_ShouldSavePendingOutboxEvent() {
        User user = createUser();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        publisher.publishPasswordResetRequested(user, "reset-token-abc");

        verify(outboxRepo).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals(10L, saved.getAggregateId());
        assertEquals("PasswordResetRequested", saved.getEventType());
        assertEquals("user.password.reset.requested", saved.getRoutingKey());
        assertEquals(OutboxEventStatus.PENDING, saved.getStatus());
        assertTrue(saved.getPayload().contains("reset-token-abc"));
    }
}
