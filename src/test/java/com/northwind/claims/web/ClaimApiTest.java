package com.northwind.claims.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northwind.claims.fraud.FraudRules;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.Policy;
import com.northwind.claims.model.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ClaimRepository claims;

    @Autowired
    PolicyRepository policies;

    private static MockHttpServletRequestBuilder as(String userId, String role, String path) {
        return get(path).header("X-User-Id", userId).header("X-User-Role", role);
    }

    @Test
    void seedDataIsLoaded() {
        assertEquals(3, claims.count());
        assertEquals(2, policies.count());
    }

    @Test
    void customerSeesOwnClaim() throws Exception {
        mvc.perform(as("cust-ana", "CUSTOMER", "/api/claims/CLM-1001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.claim.id").value("CLM-1001"))
                .andExpect(jsonPath("$.claim.customerId").value("cust-ana"))
                .andExpect(jsonPath("$.claim.type").value("WATER_DAMAGE"))
                .andExpect(jsonPath("$.claim.status").value("APPROVED"))
                .andExpect(jsonPath("$.claim.amountCents").value(1_050_000))
                .andExpect(jsonPath("$.payableCents").value(1_000_000));
    }

    @Test
    void payableAppliesTheDeductibleOfTheClaimsPolicy() throws Exception {
        mvc.perform(as("cust-ana", "CUSTOMER", "/api/claims/CLM-1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payableCents").value(270_000));
        mvc.perform(as("cust-ben", "CUSTOMER", "/api/claims/CLM-2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payableCents").value(2_000_000));
    }

    @Test
    void customerListsOnlyOwnClaims() throws Exception {
        mvc.perform(as("cust-ana", "CUSTOMER", "/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder("CLM-1001", "CLM-1002")))
                .andExpect(jsonPath("$[*].customerId", everyItem(is("cust-ana"))));
    }

    @Test
    void customerWithNoClaimsGetsAnEmptyList() throws Exception {
        mvc.perform(as("cust-nobody", "CUSTOMER", "/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void customerCannotSeeAnotherCustomersClaim() throws Exception {
        mvc.perform(as("cust-ana", "CUSTOMER", "/api/claims/CLM-2001"))
                .andExpect(status().isNotFound());
        mvc.perform(as("cust-ben", "CUSTOMER", "/api/claims/CLM-1001"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADJUSTER", "FINANCE"})
    void staffListAllClaims(String role) throws Exception {
        mvc.perform(as("staff-1", role, "/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder("CLM-1001", "CLM-1002", "CLM-2001")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADJUSTER", "FINANCE"})
    void staffSeeAnyCustomersClaim(String role) throws Exception {
        mvc.perform(as("staff-1", role, "/api/claims/CLM-2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.customerId").value("cust-ben"))
                .andExpect(jsonPath("$.payableCents").value(2_000_000));
    }

    @Test
    void unknownClaimIsNotFound() throws Exception {
        mvc.perform(as("adj-1", "ADJUSTER", "/api/claims/CLM-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestsWithoutIdentityAreRejected() throws Exception {
        mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/CLM-1001")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingRoleIsRejected() throws Exception {
        mvc.perform(get("/api/claims").header("X-User-Id", "cust-ana")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrBlankUserIdIsRejected() throws Exception {
        mvc.perform(get("/api/claims").header("X-User-Role", "ADJUSTER")).andExpect(status().isUnauthorized());
        mvc.perform(as(" ", "ADJUSTER", "/api/claims")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "adjuster", ""})
    void unknownRoleIsRejected(String role) throws Exception {
        mvc.perform(as("cust-ana", role, "/api/claims")).andExpect(status().isUnauthorized());
    }

    @Test
    void seededClaimsScoreBelowTheInvestigationThreshold() {
        for (Claim claim : claims.findAll()) {
            Policy policy = policies.findById(claim.getPolicyId()).orElseThrow();
            int score = FraudRules.score(claim, policy, 0);
            assertFalse(FraudRules.needsInvestigation(score), claim.getId() + " scored " + score);
        }
        Claim theft = claims.findById("CLM-2001").orElseThrow();
        assertEquals(15, FraudRules.score(theft, policies.findById("POL-2002").orElseThrow(), 0));
    }
}
