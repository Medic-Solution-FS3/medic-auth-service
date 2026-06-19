package com.medic.auth.domain.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("User not found");
    }

    public UserNotFoundException(String email) {
        super("User not found");
    }
}
