package com.medic.auth.infrastructure.rest;

import com.medic.auth.application.service.AuthService;
import com.medic.auth.infrastructure.rest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User registration, email verification, login, token management and password reset")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user",
            description = "Creates an inactive user account and sends an email verification link. " +
                    "The account is only activated after the user verifies their email.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered — verification email sent"),
            @ApiResponse(responseCode = "400", description = "Validation error (invalid email, weak password, etc.)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.email(), request.password(), request.fullName(), request.phone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Registration successful. Please check your email to verify your account."));
    }

    @Operation(summary = "Verify email address",
            description = "Activates the user account using the token sent via email. " +
                    "The token expires after 24 hours.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified — account activated"),
            @ApiResponse(responseCode = "401", description = "Invalid or already-used token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Token expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now login."));
    }

    @Operation(summary = "Login",
            description = "Authenticates a verified user and returns a short-lived JWT access token (RS256) " +
                    "and a long-lived refresh token (UUID). Any previous refresh tokens are revoked on login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated — access and refresh tokens returned",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or email not verified",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh access token",
            description = "Issues a new access token and rotates the refresh token. " +
                    "The previous refresh token is immediately revoked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid or revoked",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Refresh token expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        LoginResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Request password reset",
            description = "Sends a password reset email if the address is registered. " +
                    "Always returns 200 to avoid revealing whether the email exists (anti-enumeration).")
    @ApiResponse(responseCode = "200", description = "Request processed — reset email sent if address is known")
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(Map.of("message", "If the email exists, a password reset link has been sent."));
    }

    @Operation(summary = "Reset password",
            description = "Sets a new password using the token from the reset email. " +
                    "On success, all active sessions (refresh tokens) are revoked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset — all sessions invalidated"),
            @ApiResponse(responseCode = "400", description = "New password does not meet complexity requirements",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Reset token invalid or already used",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Reset token expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now login with your new password."));
    }

    @Operation(summary = "Resend verification email",
            description = "Generates a new verification token and resends the email. " +
                    "Always returns 200 to avoid revealing whether the email exists.")
    @ApiResponse(responseCode = "200", description = "Request processed — new verification email sent if applicable")
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(Map.of("message", "If the email exists and is not yet verified, a new verification email has been sent."));
    }

    @Operation(summary = "Logout",
            description = "Revokes the provided refresh token. The access token remains valid until it expires naturally.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Refresh token revoked"),
            @ApiResponse(responseCode = "401", description = "Refresh token not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }
}
