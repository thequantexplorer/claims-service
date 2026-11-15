package com.northwind.claims.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northwind.claims.auth.AuthFilter;
import com.northwind.claims.auth.GatewaySignature;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "northwind.gateway.signing-secret=" + ClaimApiTest.SECRET)
@AutoConfigureMockMvc
class ClaimApiTest {
    static final String SECRET = "test-gateway-secret";

    @Autowired
    MockMvc mvc;

    static MockHttpServletRequestBuilder signedGet(String path, String userId, String role) {
        return signedGet(path, userId, role, Instant.now().getEpochSecond());
    }

    static MockHttpServletRequestBuilder signedGet(String path, String userId, String role, long epochSeconds) {
        String timestamp = Long.toString(epochSeconds);
        return get(path)
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .header(AuthFilter.TIMESTAMP_HEADER, timestamp)
                .header(AuthFilter.SIGNATURE_HEADER, GatewaySignature.sign(SECRET, userId, role, timestamp, "GET", path));
    }

    @Test
    void customerSeesOwnClaim() throws Exception {
        mvc.perform(signedGet("/api/claims/CLM-1001", "cust-ana", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payableCents").value(1_000_000));
    }

    @Test
    void customerCannotSeeAnotherCustomersClaim() throws Exception {
        mvc.perform(signedGet("/api/claims/CLM-2001", "cust-ana", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedAdjusterSeesAllClaims() throws Exception {
        mvc.perform(signedGet("/api/claims", "adj-1", "ADJUSTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customerId != 'cust-ana')]").exists());
    }

    @Test
    void requestsWithoutIdentityAreRejected() throws Exception {
        mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedIdentityHeadersAreRejected() throws Exception {
        mvc.perform(get("/api/claims").header("X-User-Id", "anything").header("X-User-Role", "ADJUSTER"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/CLM-2001").header("X-User-Id", "anything").header("X-User-Role", "ADJUSTER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signatureFromAnotherSecretIsRejected() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mvc.perform(get("/api/claims")
                        .header("X-User-Id", "anything")
                        .header("X-User-Role", "ADJUSTER")
                        .header(AuthFilter.TIMESTAMP_HEADER, timestamp)
                        .header(AuthFilter.SIGNATURE_HEADER,
                                GatewaySignature.sign("guessed", "anything", "ADJUSTER", timestamp, "GET", "/api/claims")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerSignatureCannotBeReusedToEscalateRole() throws Exception {
        mvc.perform(withHeaders("/api/claims", "cust-ana", "ADJUSTER", "cust-ana", "CUSTOMER", "/api/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signatureCannotBeReusedForAnotherCustomer() throws Exception {
        mvc.perform(withHeaders("/api/claims", "cust-ben", "CUSTOMER", "cust-ana", "CUSTOMER", "/api/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signatureCannotBeReusedForAnotherPath() throws Exception {
        mvc.perform(withHeaders("/api/claims/CLM-2001", "cust-ana", "CUSTOMER", "cust-ana", "CUSTOMER", "/api/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateIdentityHeadersAreRejected() throws Exception {
        mvc.perform(signedGet("/api/claims", "cust-ana", "CUSTOMER").header("X-User-Role", "ADJUSTER"))
                .andExpect(status().isUnauthorized());
    }

    /** Sends {@code sentId}/{@code sentRole} on {@code path} with a valid signature for different values. */
    private static MockHttpServletRequestBuilder withHeaders(String path, String sentId, String sentRole,
            String signedId, String signedRole, String signedPath) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        return get(path)
                .header("X-User-Id", sentId)
                .header("X-User-Role", sentRole)
                .header(AuthFilter.TIMESTAMP_HEADER, timestamp)
                .header(AuthFilter.SIGNATURE_HEADER,
                        GatewaySignature.sign(SECRET, signedId, signedRole, timestamp, "GET", signedPath));
    }

    @Test
    void staleSignatureIsRejected() throws Exception {
        long anHourAgo = Instant.now().minusSeconds(3600).getEpochSecond();
        mvc.perform(signedGet("/api/claims", "cust-ana", "CUSTOMER", anHourAgo))
                .andExpect(status().isUnauthorized());
    }
}
