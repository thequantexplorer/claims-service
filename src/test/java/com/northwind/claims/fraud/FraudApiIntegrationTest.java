package com.northwind.claims.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import com.northwind.claims.model.PolicyRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FraudApiIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ClaimRepository claims;

    @Autowired
    PolicyRepository policies;

    private String fetch(String id, String userId, String role) throws Exception {
        return mvc.perform(get("/api/claims/" + id).header("X-User-Id", userId).header("X-User-Role", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.id").value(id))
                .andReturn().getResponse().getContentAsString();
    }

    /** Rebuilds the claim from the API response so the score is computed from what the API actually returned. */
    private static Claim claimFromJson(String json) {
        return new Claim(
                JsonPath.read(json, "$.claim.id"),
                JsonPath.read(json, "$.claim.policyId"),
                JsonPath.read(json, "$.claim.customerId"),
                JsonPath.read(json, "$.claim.payeeName"),
                ClaimType.valueOf(JsonPath.read(json, "$.claim.type")),
                ClaimStatus.valueOf(JsonPath.read(json, "$.claim.status")),
                ((Number) JsonPath.read(json, "$.claim.amountCents")).longValue(),
                JsonPath.read(json, "$.claim.installments"),
                LocalDate.parse(JsonPath.read(json, "$.claim.incidentDate")),
                LocalDate.parse(JsonPath.read(json, "$.claim.reportedDate")));
    }

    private Policy policyOf(Claim claim) {
        return policies.findById(claim.getPolicyId()).orElseThrow();
    }

    private int priorClaimsThisYear(Claim claim) {
        List<Claim> sameCustomer = claims.findByCustomerId(claim.getCustomerId());
        return (int) sameCustomer.stream()
                .filter(c -> !c.getId().equals(claim.getId()))
                .filter(c -> c.getReportedDate().getYear() == claim.getReportedDate().getYear())
                .count();
    }

    @Test
    void seededTheftClaimFetchedByAdjusterScoresFifteenAndIsNotInvestigated() throws Exception {
        Claim claim = claimFromJson(fetch("CLM-2001", "adj-1", "ADJUSTER"));
        assertEquals(ClaimType.THEFT, claim.getType());
        assertEquals(0, priorClaimsThisYear(claim));

        int score = FraudRules.score(claim, policyOf(claim), priorClaimsThisYear(claim));

        assertEquals(15, score);
        assertFalse(FraudRules.needsInvestigation(score));
    }

    @Test
    void seededTheftClaimFetchedByOwnerScoresTheSameAsForAdjuster() throws Exception {
        Claim asOwner = claimFromJson(fetch("CLM-2001", "cust-ben", "CUSTOMER"));
        Claim asAdjuster = claimFromJson(fetch("CLM-2001", "adj-1", "ADJUSTER"));

        assertEquals(15, FraudRules.score(asOwner, policyOf(asOwner), 0));
        assertEquals(FraudRules.score(asAdjuster, policyOf(asAdjuster), 0),
                FraudRules.score(asOwner, policyOf(asOwner), 0));
    }

    @Test
    void seededWaterDamageClaimFetchedByOwnerScoresZero() throws Exception {
        Claim claim = claimFromJson(fetch("CLM-1001", "cust-ana", "CUSTOMER"));
        assertEquals(1, priorClaimsThisYear(claim));

        int score = FraudRules.score(claim, policyOf(claim), priorClaimsThisYear(claim));

        assertEquals(0, score);
        assertFalse(FraudRules.needsInvestigation(score));
    }

    @Test
    void seededAutoClaimFetchedByFinanceScoresZero() throws Exception {
        Claim claim = claimFromJson(fetch("CLM-1002", "fin-1", "FINANCE"));
        assertEquals(ClaimType.AUTO, claim.getType());

        int score = FraudRules.score(claim, policyOf(claim), priorClaimsThisYear(claim));

        assertEquals(0, score);
        assertFalse(FraudRules.needsInvestigation(score));
    }

    @Test
    void seededClaimWithTwoPriorClaimsAddsRepeatClaimantPoints() throws Exception {
        Claim claim = claimFromJson(fetch("CLM-2001", "fin-1", "FINANCE"));

        assertEquals(40, FraudRules.score(claim, policyOf(claim), 2));
    }

    @Test
    void noSeededClaimListedForAdjusterNeedsInvestigation() throws Exception {
        String json = mvc.perform(get("/api/claims").header("X-User-Id", "adj-1").header("X-User-Role", "ADJUSTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$[*].id");

        for (String id : ids) {
            Claim claim = claimFromJson(fetch(id, "adj-1", "ADJUSTER"));
            int score = FraudRules.score(claim, policyOf(claim), priorClaimsThisYear(claim));
            assertEquals(claim.getType() == ClaimType.THEFT ? 15 : 0, score, id);
            assertFalse(FraudRules.needsInvestigation(score), id);
        }
    }

    @Test
    void customerCannotFetchAnotherCustomersTheftClaimForScoring() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimCannotBeFetchedForScoringWithoutGatewayHeaders() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001")).andExpect(status().isUnauthorized());
    }
}
