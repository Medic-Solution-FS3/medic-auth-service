package com.medic.auth.infrastructure.security;

import com.medic.auth.domain.model.User;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final RsaKeyProvider rsaKeyProvider;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.issuer}")
    private String issuer;

    public JwtTokenProvider(RsaKeyProvider rsaKeyProvider) {
        this.rsaKeyProvider = rsaKeyProvider;
    }

    /**
     * Generates a signed RS256 JWT access token for the given user.
     * <p>
     * Token claims: {@code sub} = userId, {@code roles} = list of role names,
     * {@code iss} = configured issuer, {@code iat} / {@code exp} = issue and expiry timestamps.
     * The header carries {@code kid=medic-auth-rs256} so downstream verifiers can select the
     * correct public key from the JWKS endpoint ({@code /.well-known/jwks.json}).
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .header().keyId(RsaKeyProvider.KEY_ID).and()
                .subject(user.getId().toString())
                .claim("roles", List.of(user.getRole().getName().name()))
                .issuedAt(now)
                .expiration(expiryDate)
                .issuer(issuer)
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(rsaKeyProvider.getPublicKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
