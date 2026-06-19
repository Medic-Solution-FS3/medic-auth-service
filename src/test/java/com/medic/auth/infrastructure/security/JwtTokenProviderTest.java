package com.medic.auth.infrastructure.security;

import com.medic.auth.domain.model.Role;
import com.medic.auth.domain.model.User;
import com.medic.auth.domain.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private RsaKeyProvider rsaKeyProvider;

    @BeforeEach
    void setUp() {
        rsaKeyProvider = new RsaKeyProvider();
        tokenProvider = new JwtTokenProvider(rsaKeyProvider);
        ReflectionTestUtils.setField(tokenProvider, "expiration", 3600000L);
        ReflectionTestUtils.setField(tokenProvider, "issuer", "medic-auth-service");
    }

    private User createUser() {
        Role role = new Role(UserRole.PACIENTE);
        role.setId(1L);
        User user = new User();
        user.setId(42L);
        user.setEmail("test@test.com");
        user.setPasswordHash("hash");
        user.setFullName("Test User");
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return user;
    }

    @Test
    void generateAccessToken_ShouldContainExpectedClaims() {
        User user = createUser();
        String token = tokenProvider.generateAccessToken(user);

        assertNotNull(token);

        Claims claims = Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("42", claims.getSubject());
        assertEquals("medic-auth-service", claims.getIssuer());
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getIssuedAt());
    }

    @Test
    void generateAccessToken_ShouldHaveCorrectKeyId() {
        User user = createUser();
        String token = tokenProvider.generateAccessToken(user);

        String[] parts = token.split("\\.");
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
        assertTrue(headerJson.contains(RsaKeyProvider.KEY_ID));
    }

    @Test
    void generateAccessToken_ShouldContainRolesClain() {
        User user = createUser();
        String token = tokenProvider.generateAccessToken(user);

        Claims claims = Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertNotNull(claims.get("roles"));
    }

    @Test
    void validateToken_ShouldReturnTrue_WhenTokenIsValid() {
        User user = createUser();
        String token = tokenProvider.generateAccessToken(user);
        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenTokenIsExpired() {
        User user = createUser();
        ReflectionTestUtils.setField(tokenProvider, "expiration", -1000L);
        String token = tokenProvider.generateAccessToken(user);
        assertFalse(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenTokenSignedWithDifferentKey() {
        User user = createUser();

        // Create a token signed with a different key
        RsaKeyProvider otherKey = new RsaKeyProvider();
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherKey);
        ReflectionTestUtils.setField(otherProvider, "expiration", 3600000L);
        ReflectionTestUtils.setField(otherProvider, "issuer", "medic-auth-service");

        String tokenFromOtherKey = otherProvider.generateAccessToken(user);

        // The original tokenProvider should reject a token signed by another key
        assertFalse(tokenProvider.validateToken(tokenFromOtherKey));
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenTokenIsMalformed() {
        assertFalse(tokenProvider.validateToken("not.a.valid.jwt"));
    }

    @Test
    void getUserIdFromToken_ShouldReturnCorrectUserId() {
        User user = createUser();
        String token = tokenProvider.generateAccessToken(user);

        Long userId = tokenProvider.getUserIdFromToken(token);
        assertEquals(42L, userId);
    }
}
