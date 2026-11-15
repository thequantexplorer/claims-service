package com.northwind.claims.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "northwind.gateway.signing-secret=")
@AutoConfigureMockMvc
class UnconfiguredGatewaySecretTest {
    @Autowired
    MockMvc mvc;

    @Test
    void allApiRequestsAreRejectedWhenNoSecretIsConfigured() throws Exception {
        mvc.perform(ClaimApiTest.signedGet("/api/claims", "cust-ana", "CUSTOMER")).andExpect(status().isUnauthorized());
    }
}
