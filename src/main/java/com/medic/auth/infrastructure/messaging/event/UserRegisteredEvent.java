package com.medic.auth.infrastructure.messaging.event;

import com.medic.auth.domain.model.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserRegisteredEvent(
        String version,
        String eventId,
        Long userId,
        String email,
        String fullName,
        String phone,
        String role,
        String verificationToken,
        String occurredAt
) {
    public static UserRegisteredEvent from(User user, String verificationToken) {
        return new UserRegisteredEvent(
                "v1",
                UUID.randomUUID().toString(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().getName().name(),
                verificationToken,
                LocalDateTime.now().toString()
        );
    }
}
