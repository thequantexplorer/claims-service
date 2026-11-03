package com.northwind.claims.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import com.northwind.claims.model.PolicyRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IntakeApiIntegrationTest {
    private static final LocalDate SEED_DATE = LocalDate.of(2026, 10, 1);

    @Autowired
    MockMvc mvc;

    @Autowired
    ClaimRepository claims;

    @Autowired
    PolicyRepository policies;

    private String fetch(String path, String userId, String role) throws Exception {
        return mvc.perform(get(path).header("X-User-Id", userId).header("X-User-Role", role))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static Claim toClaim(Map<String, Object> json) {
        return new Claim(
                (String) json.get("id"),
                (String) json.get("policyId"),
                (String) json.get("customerId"),
                (String) json.get("payeeName"),
                ClaimType.valueOf((String) json.get("type")),
                ClaimStatus.valueOf((String) json.get("status")),
                ((Number) json.get("amountCents")).longValue(),
                ((Number) json.get("installments")).intValue(),
                LocalDate.parse((String) json.get("incidentDate")),
                LocalDate.parse((String) json.get("reportedDate")));
    }

    @Test
    void ownerFetchesClm1001AndItPassesIntake() throws Exception {
        DocumentContext body = JsonPath.parse(fetch("/api/claims/CLM-1001", "cust-ana", "CUSTOMER"));
        Claim claim = toClaim(body.read("$.claim"));

        assertEquals("Ana Ruiz", claim.getPayeeName());
        assertEquals(1_050_000, claim.getAmountCents());
        assertEquals(3, claim.getInstallments());
        assertEquals(SEED_DATE, claim.getIncidentDate());
        assertEquals(SEED_DATE.plusDays(2), claim.getReportedDate());
        assertEquals(1_000_000, ((Number) body.read("$.payableCents")).longValue());
        assertEquals(List.of(), ClaimIntakeValidator.validate(claim, claim.getReportedDate()));

        Policy policy = policies.findById(claim.getPolicyId()).orElseThrow();
        assertEquals("cust-ana", policy.getCustomerId());
    }

    @Test
    void financeFetchesClm2001AndItPassesIntake() throws Exception {
        DocumentContext body = JsonPath.parse(fetch("/api/claims/CLM-2001", "fin-1", "FINANCE"));
        Claim claim = toClaim(body.read("$.claim"));

        assertEquals("Ben Okafor", claim.getPayeeName());
        assertEquals(6, claim.getInstallments());
        assertEquals(4, ChronoUnit.DAYS.between(claim.getIncidentDate(), claim.getReportedDate()));
        assertEquals(2_000_000, ((Number) body.read("$.payableCents")).longValue());
        assertEquals(List.of(), ClaimIntakeValidator.validate(claim, claim.getReportedDate()));
        assertEquals(List.of(), ClaimIntakeValidator.validate(claim, LocalDate.of(2030, 1, 1)));
    }

    @Test
    void adjusterListsAllSeededClaimsAndEveryOnePassesIntake() throws Exception {
        List<Map<String, Object>> list = JsonPath.read(fetch("/api/claims", "adj-1", "ADJUSTER"), "$");
        assertEquals(3, list.size());
        for (Map<String, Object> json : list) {
            Claim claim = toClaim(json);
            assertEquals(List.of(), ClaimIntakeValidator.validate(claim, claim.getReportedDate()), claim.getId());
        }
    }

    @Test
    void apiClaimMatchesRepositoryAndValidatesIdentically() throws Exception {
        DocumentContext body = JsonPath.parse(fetch("/api/claims/CLM-1002", "adj-1", "ADJUSTER"));
        Claim fromApi = toClaim(body.read("$.claim"));
        Claim fromDb = claims.findById("CLM-1002").orElseThrow();

        assertEquals(fromDb.getIncidentDate(), fromApi.getIncidentDate());
        assertEquals(fromDb.getReportedDate(), fromApi.getReportedDate());
        assertEquals(fromDb.getInstallments(), fromApi.getInstallments());
        assertEquals(fromDb.getAmountCents(), fromApi.getAmountCents());
        assertEquals(ClaimIntakeValidator.validate(fromDb, fromDb.getReportedDate()),
                ClaimIntakeValidator.validate(fromApi, fromApi.getReportedDate()));
        assertEquals(270_000, ((Number) body.read("$.payableCents")).longValue());
    }

    @Test
    void seededClaimIsFlaggedAsFutureIncidentWhenValidatedBeforeItHappened() throws Exception {
        Claim claim = toClaim(JsonPath.read(fetch("/api/claims/CLM-1002", "cust-ana", "CUSTOMER"), "$.claim"));
        assertEquals(SEED_DATE.plusDays(10), claim.getIncidentDate());
        assertEquals(List.of("incident date is in the future"),
                ClaimIntakeValidator.validate(claim, claim.getIncidentDate().minusDays(1)));
        assertEquals(List.of(), ClaimIntakeValidator.validate(claim, claim.getIncidentDate()));
    }

    @Test
    void customerListContainsOnlyOwnClaimsAndAllPassIntake() throws Exception {
        List<Map<String, Object>> list = JsonPath.read(fetch("/api/claims", "cust-ana", "CUSTOMER"), "$");
        assertEquals(2, list.size());
        for (Map<String, Object> json : list) {
            Claim claim = toClaim(json);
            assertEquals("cust-ana", claim.getCustomerId());
            assertEquals(List.of(), ClaimIntakeValidator.validate(claim, claim.getReportedDate()));
        }
    }

    @Test
    void customerCannotFetchAnotherCustomersClaimForIntake() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001").header("X-User-Id", "cust-ben").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimRequestWithoutGatewayHeadersIsRejected() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001")).andExpect(status().isUnauthorized());
    }

    @Test
    void claimRequestWithUnknownRoleIsRejected() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001").header("X-User-Id", "cust-ana").header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }
}
