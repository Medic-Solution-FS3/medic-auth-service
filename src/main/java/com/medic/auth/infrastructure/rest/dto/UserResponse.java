package com.medic.auth.infrastructure.rest.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String role,
        Boolean active,
        Boolean emailVerified,
        LocalDateTime createdAt
) {}
