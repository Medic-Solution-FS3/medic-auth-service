package com.medic.auth.infrastructure.messaging.event;

import com.medic.auth.domain.model.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmailVerifiedEvent(
        String version,
        String eventId,
        Long userId,
        String email,
        String fullName,
        String occurredAt
) {
    public static EmailVerifiedEvent from(User user) {
        return new EmailVerifiedEvent(
                "v1",
                UUID.randomUUID().toString(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                LocalDateTime.now().toString()
        );
    }
}
