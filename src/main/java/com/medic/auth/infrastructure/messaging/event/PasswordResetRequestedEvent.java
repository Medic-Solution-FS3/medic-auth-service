package com.medic.auth.infrastructure.messaging.event;

import com.medic.auth.domain.model.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record PasswordResetRequestedEvent(
        String version,
        String eventId,
        Long userId,
        String email,
        String fullName,
        String resetToken,
        String occurredAt
) {
    public static PasswordResetRequestedEvent from(User user, String resetToken) {
        return new PasswordResetRequestedEvent(
                "v1",
                UUID.randomUUID().toString(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                resetToken,
                LocalDateTime.now().toString()
        );
    }
}
