package com.medic.auth.infrastructure.rest.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String email
) {}
