package com.medic.auth.infrastructure.rest;

import com.medic.auth.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class TestController {
        @GetMapping("/test/user-not-found")
        public void userNotFound() { throw new UserNotFoundException(1L); }

        @GetMapping("/test/user-already-exists")
        public void userAlreadyExists() { throw new UserAlreadyExistsException("test@test.com"); }

        @GetMapping("/test/invalid-credentials")
        public void invalidCredentials() { throw new InvalidCredentialsException(); }

        @GetMapping("/test/email-not-verified")
        public void emailNotVerified() { throw new EmailNotVerifiedException("Email not verified"); }

        @GetMapping("/test/invalid-token")
        public void invalidToken() { throw new InvalidTokenException("Bad token"); }

        @GetMapping("/test/token-expired")
        public void tokenExpired() { throw new TokenExpiredException("Token expired"); }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handleUserNotFoundException_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/test/user-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void handleUserAlreadyExistsException_ShouldReturn409() throws Exception {
        mockMvc.perform(get("/test/user-already-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void handleInvalidCredentialsException_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/test/invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void handleEmailNotVerifiedException_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/test/email-not-verified"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void handleInvalidTokenException_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/test/invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void handleTokenExpiredException_ShouldReturn410() throws Exception {
        mockMvc.perform(get("/test/token-expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Gone"));
    }
}
