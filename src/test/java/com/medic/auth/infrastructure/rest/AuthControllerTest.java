package com.medic.auth.infrastructure.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medic.auth.application.service.AuthService;
import com.medic.auth.domain.exception.*;
import com.medic.auth.infrastructure.rest.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_ShouldReturn201_WhenRequestIsValid() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@test.com",
                "password", "Password1!",
                "fullName", "Test User",
                "phone", "+56912345678"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).register("user@test.com", "Password1!", "Test User", "+56912345678");
    }

    @Test
    void register_ShouldReturn400_WhenEmailIsInvalid() throws Exception {
        Map<String, String> body = Map.of(
                "email", "not-an-email",
                "password", "Password1!",
                "fullName", "Test User",
                "phone", "+56912345678"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(), any(), any(), any());
    }

    @Test
    void register_ShouldReturn400_WhenPasswordIsWeak() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@test.com",
                "password", "weakpassword",
                "fullName", "Test User",
                "phone", "+56912345678"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_ShouldReturn409_WhenEmailAlreadyExists() throws Exception {
        doThrow(new UserAlreadyExistsException("user@test.com"))
                .when(authService).register(any(), any(), any(), any());

        Map<String, String> body = Map.of(
                "email", "user@test.com",
                "password", "Password1!",
                "fullName", "Test User",
                "phone", "+56912345678"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void verifyEmail_ShouldReturn200_WhenTokenIsValid() throws Exception {
        Map<String, String> body = Map.of("token", "valid-token");

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).verifyEmail("valid-token");
    }

    @Test
    void verifyEmail_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
        doThrow(new InvalidTokenException("Invalid token")).when(authService).verifyEmail("bad-token");

        Map<String, String> body = Map.of("token", "bad-token");

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_ShouldReturn200_WithLoginResponse_WhenCredentialsAreValid() throws Exception {
        LoginResponse loginResponse = new LoginResponse("access.token", "refresh.token", 1L, "user@test.com");
        when(authService.login("user@test.com", "Password1!")).thenReturn(loginResponse);

        Map<String, String> body = Map.of("email", "user@test.com", "password", "Password1!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.token"));
    }

    @Test
    void login_ShouldReturn401_WhenCredentialsAreInvalid() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        Map<String, String> body = Map.of("email", "user@test.com", "password", "wrong");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_ShouldReturn200_WhenRefreshTokenIsValid() throws Exception {
        LoginResponse loginResponse = new LoginResponse("new.access.token", "new.refresh.token", 1L, "user@test.com");
        when(authService.refreshToken("valid-refresh")).thenReturn(loginResponse);

        Map<String, String> body = Map.of("refreshToken", "valid-refresh");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"));
    }

    @Test
    void refresh_ShouldReturn401_WhenRefreshTokenIsInvalid() throws Exception {
        when(authService.refreshToken("bad-refresh")).thenThrow(new InvalidTokenException("Invalid token"));

        Map<String, String> body = Map.of("refreshToken", "bad-refresh");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_ShouldReturn200_Always() throws Exception {
        Map<String, String> body = Map.of("email", "someone@test.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).forgotPassword("someone@test.com");
    }

    @Test
    void resetPassword_ShouldReturn200_WhenRequestIsValid() throws Exception {
        Map<String, String> body = Map.of("token", "reset-token", "newPassword", "NewPassword1!");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).resetPassword("reset-token", "NewPassword1!");
    }

    @Test
    void resetPassword_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
        doThrow(new InvalidTokenException("Invalid token"))
                .when(authService).resetPassword(eq("bad-token"), anyString());

        Map<String, String> body = Map.of("token", "bad-token", "newPassword", "NewPassword1!");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resendVerification_ShouldReturn200_Always() throws Exception {
        Map<String, String> body = Map.of("email", "user@test.com");

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).resendVerification("user@test.com");
    }

    @Test
    void logout_ShouldReturn204_WhenRefreshTokenIsValid() throws Exception {
        Map<String, String> body = Map.of("refreshToken", "valid-refresh-token");

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        verify(authService).logout("valid-refresh-token");
    }
}
