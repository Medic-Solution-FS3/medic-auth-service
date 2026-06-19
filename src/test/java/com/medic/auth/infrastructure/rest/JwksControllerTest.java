package com.medic.auth.infrastructure.rest;

import com.medic.auth.infrastructure.security.RsaKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwksControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RsaKeyProvider rsaKeyProvider = new RsaKeyProvider();
        JwksController controller = new JwksController(rsaKeyProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void jwks_ShouldReturn200_WithValidJwksStructure() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("medic-auth-rs256"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty());
    }
}
