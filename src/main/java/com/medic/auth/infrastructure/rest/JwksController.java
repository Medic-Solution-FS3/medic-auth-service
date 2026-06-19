package com.medic.auth.infrastructure.rest;

import com.medic.auth.infrastructure.security.RsaKeyProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/.well-known")
@Tag(name = "JWKS", description = "Public key endpoint for JWT signature verification (RS256)")
public class JwksController {

    private final Map<String, Object> jwksResponse;

    public JwksController(RsaKeyProvider rsaKeyProvider) {
        RSAPublicKey publicKey = rsaKeyProvider.getPublicKey();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", RsaKeyProvider.KEY_ID,
                "n",   encoder.encodeToString(unsignedBytes(publicKey.getModulus())),
                "e",   encoder.encodeToString(unsignedBytes(publicKey.getPublicExponent()))
        );
        this.jwksResponse = Map.of("keys", List.of(jwk));
    }

    @Operation(summary = "JSON Web Key Set",
            description = "Returns the RSA-2048 public key used to verify JWT access token signatures (RS256). " +
                    "Downstream services and API gateways should fetch this endpoint to validate tokens " +
                    "without calling the auth service on every request.")
    @ApiResponse(responseCode = "200", description = "JWKS document with the active RSA public key")
    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return jwksResponse;
    }

    private static byte[] unsignedBytes(BigInteger n) {
        byte[] bytes = n.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }
}
