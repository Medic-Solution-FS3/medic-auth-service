package com.medic.auth.infrastructure.security;

import com.medic.auth.domain.model.Role;
import com.medic.auth.domain.model.User;
import com.medic.auth.domain.model.UserRole;
import com.medic.auth.infrastructure.persistence.JpaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private JpaUserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userRepository);
    }

    private User createActiveUser() {
        Role role = new Role(UserRole.PACIENTE);
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setPasswordHash("hashedPassword");
        user.setFullName("Test User");
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return user;
    }

    @Test
    void loadUserByUsername_ShouldReturnUserDetails_WhenUserExists() {
        User user = createActiveUser();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("test@test.com");

        assertEquals("test@test.com", details.getUsername());
        assertEquals("hashedPassword", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PACIENTE".equals(a.getAuthority())));
        assertTrue(details.isEnabled());
    }

    @Test
    void loadUserByUsername_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("nobody@test.com"));
    }

    @Test
    void loadUserByUsername_ShouldReturnDisabledUser_WhenUserIsInactive() {
        User user = createActiveUser();
        user.setActive(false);
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("test@test.com");

        assertFalse(details.isEnabled());
        assertTrue(details.isAccountNonLocked() == false);
    }

    @Test
    void loadUserById_ShouldReturnUserDetails_WhenUserExists() {
        User user = createActiveUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserById(1L);

        assertEquals("test@test.com", details.getUsername());
    }

    @Test
    void loadUserById_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserById(99L));
    }
}
