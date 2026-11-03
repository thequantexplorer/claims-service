package com.northwind.claims.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void customerSeesOwnClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payableCents").value(1_000_000));
    }

    @Test
    void customerCannotSeeAnotherCustomersClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestsWithoutIdentityAreRejected() throws Exception {
        mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
    }
}
