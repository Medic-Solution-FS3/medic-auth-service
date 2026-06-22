package com.medic.auth.infrastructure.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medic.auth.application.service.AuthService;
import com.medic.auth.domain.exception.UserNotFoundException;
import com.medic.auth.domain.model.Role;
import com.medic.auth.domain.model.User;
import com.medic.auth.domain.model.UserRole;
import com.medic.auth.infrastructure.rest.dto.UpdateProfileRequest;
import com.medic.auth.infrastructure.rest.dto.UserResponse;
import com.medic.auth.infrastructure.security.CustomUserDetailsService;
import com.medic.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private User createUser(Long id) {
        Role role = new Role(UserRole.PACIENTE);
        role.setId(1L);
        User user = new User();
        user.setId(id);
        user.setEmail("user@test.com");
        user.setFullName("Test User");
        user.setPhone("+56912345678");
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return user;
    }

    private UserResponse createUserResponse(Long id) {
        return new UserResponse(id, "user@test.com", "Test User", "+56912345678",
                "PACIENTE", true, true, LocalDateTime.now());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "PACIENTE")
    void getCurrentUser_ShouldReturnProfile_WhenAuthenticated() throws Exception {
        User user = createUser(1L);
        when(authService.getUserByEmail("user@test.com")).thenReturn(user);

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.role").value("PACIENTE"));
    }

    @Test
    void getCurrentUser_ShouldReturn401_WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "PACIENTE")
    void updateCurrentUser_ShouldReturnUpdatedProfile_WhenRequestIsValid() throws Exception {
        User user = createUser(1L);
        UserResponse response = createUserResponse(1L);

        when(authService.getUserByEmail("user@test.com")).thenReturn(user);
        when(authService.updateProfile(eq(1L), eq("New Name"), eq("+56999999999"))).thenReturn(response);

        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "+56999999999");

        mockMvc.perform(patch("/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    void updateCurrentUser_ShouldReturn401_WhenNotAuthenticated() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "+56999999999");

        mockMvc.perform(patch("/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getUserById_ShouldReturnUser_WhenAdminRequests() throws Exception {
        User user = createUser(5L);
        when(authService.getUserById(5L)).thenReturn(user);

        mockMvc.perform(get("/users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "PACIENTE")
    void getUserById_ShouldReturnOwnProfile_WhenPacienteRequestsOwnId() throws Exception {
        User user = createUser(1L);
        when(authService.getUserByEmail("user@test.com")).thenReturn(user);
        when(authService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getUserById_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
        when(authService.getUserById(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserById_ShouldReturn401_WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isUnauthorized());
    }
}
