package com.northwind.claims.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.Policy;
import com.northwind.claims.model.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CoverageApiIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ClaimRepository claims;

    @Autowired
    PolicyRepository policies;

    private String fetch(String id, String userId, String role) throws Exception {
        return mvc.perform(get("/api/claims/" + id).header("X-User-Id", userId).header("X-User-Role", role))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long payableFrom(String json) {
        return ((Number) JsonPath.read(json, "$.payableCents")).longValue();
    }

    @ParameterizedTest
    @CsvSource({
            "CLM-1001, cust-ana, CUSTOMER, 1000000",
            "CLM-1002, cust-ana, CUSTOMER, 270000",
            "CLM-2001, cust-ben, CUSTOMER, 2000000",
            "CLM-1001, adj-1, ADJUSTER, 1000000",
            "CLM-1002, adj-1, ADJUSTER, 270000",
            "CLM-2001, adj-1, ADJUSTER, 2000000",
            "CLM-1001, fin-1, FINANCE, 1000000",
            "CLM-1002, fin-1, FINANCE, 270000",
            "CLM-2001, fin-1, FINANCE, 2000000"
    })
    void payableCentsForSeededClaimIsAmountMinusDeductible(String id, String userId, String role, long expected)
            throws Exception {
        String json = fetch(id, userId, role);
        assertEquals(expected, payableFrom(json));
        assertEquals(id, JsonPath.read(json, "$.claim.id"));
    }

    @ParameterizedTest
    @CsvSource({"CLM-1001", "CLM-1002", "CLM-2001"})
    void apiPayableMatchesCalculatorOnSeededClaimAndPolicy(String id) throws Exception {
        Claim claim = claims.findById(id).orElseThrow();
        Policy policy = policies.findById(claim.getPolicyId()).orElseThrow();
        String json = fetch(id, "adj-1", "ADJUSTER");

        long amount = ((Number) JsonPath.read(json, "$.claim.amountCents")).longValue();
        assertEquals(claim.getAmountCents(), amount);
        long expected = Math.min(amount - policy.getDeductibleCents(), policy.getCoverageLimitCents());
        assertEquals(expected, payableFrom(json));
        assertEquals(CoverageCalculator.payableCents(claim, policy), payableFrom(json));
    }

    @Test
    void seededPoliciesHaveDocumentedDeductiblesAndLimits() {
        Policy ana = policies.findById("POL-1001").orElseThrow();
        assertEquals(50_000, ana.getDeductibleCents());
        assertEquals(2_500_000, ana.getCoverageLimitCents());
        Policy ben = policies.findById("POL-2002").orElseThrow();
        assertEquals(100_000, ben.getDeductibleCents());
        assertEquals(5_000_000, ben.getCoverageLimitCents());
    }

    @Test
    void payableNeverExceedsCoverageLimitForAnySeededClaim() throws Exception {
        for (Claim claim : claims.findAll()) {
            Policy policy = policies.findById(claim.getPolicyId()).orElseThrow();
            long payable = payableFrom(fetch(claim.getId(), "fin-1", "FINANCE"));
            assertEquals(true, payable >= 0 && payable <= policy.getCoverageLimitCents(), claim.getId());
        }
    }

    @Test
    void customerCannotSeePayableOfAnotherCustomersClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.payableCents").doesNotExist());
        mvc.perform(get("/api/claims/CLM-1001").header("X-User-Id", "cust-ben").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void payableRequestWithoutGatewayHeadersIsUnauthorized() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/CLM-1001").header("X-User-Id", "cust-ana"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/CLM-1001").header("X-User-Id", "cust-ana").header("X-User-Role", "ROOT"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownClaimReturns404() throws Exception {
        mvc.perform(get("/api/claims/CLM-9999").header("X-User-Id", "adj-1").header("X-User-Role", "ADJUSTER"))
                .andExpect(status().isNotFound());
    }
}
