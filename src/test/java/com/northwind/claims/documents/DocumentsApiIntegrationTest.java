package com.northwind.claims.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.ClaimType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentsApiIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ClaimRepository claims;

    private String fetch(String path, String userId, String role) throws Exception {
        return mvc.perform(get(path).header("X-User-Id", userId).header("X-User-Role", role))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static Map<String, Object> claimOf(String detailBody) {
        return JsonPath.read(detailBody, "$.claim");
    }

    private static long amountOf(Map<String, Object> claim) {
        return ((Number) claim.get("amountCents")).longValue();
    }

    private static List<String> checklistFor(Map<String, Object> claim) {
        return DocumentChecklist.required(ClaimType.valueOf((String) claim.get("type")), amountOf(claim));
    }

    @Test
    void ownerSeesWaterDamageClaimNeedingContractorReportAndEstimates() throws Exception {
        Map<String, Object> claim = claimOf(fetch("/api/claims/CLM-1001", "cust-ana", "CUSTOMER"));
        assertEquals("WATER_DAMAGE", claim.get("type"));
        assertEquals(1_050_000, amountOf(claim));
        assertEquals(List.of("claim form", "photo of damage", "plumber or contractor report", "two repair estimates"),
                checklistFor(claim));
    }

    @Test
    void ownerSeesSmallAutoClaimWithoutRepairEstimates() throws Exception {
        Map<String, Object> claim = claimOf(fetch("/api/claims/CLM-1002", "cust-ana", "CUSTOMER"));
        assertEquals("AUTO", claim.get("type"));
        assertEquals(320_000, amountOf(claim));
        assertEquals(List.of("claim form", "photo of damage", "police report or accident report"), checklistFor(claim));
    }

    @Test
    void adjusterSeesTheftClaimNeedingPoliceReportAndEstimates() throws Exception {
        Map<String, Object> claim = claimOf(fetch("/api/claims/CLM-2001", "adj-1", "ADJUSTER"));
        assertEquals("THEFT", claim.get("type"));
        assertEquals("cust-ben", claim.get("customerId"));
        assertEquals(List.of("claim form", "photo of damage", "police report", "two repair estimates"), checklistFor(claim));
    }

    @Test
    void financeListMatchesRepositoryChecklistsForAllSeededClaims() throws Exception {
        List<Map<String, Object>> list = JsonPath.read(fetch("/api/claims", "fin-1", "FINANCE"), "$");
        assertEquals(3, list.size());
        for (Map<String, Object> claim : list) {
            Claim stored = claims.findById((String) claim.get("id")).orElseThrow();
            assertEquals(DocumentChecklist.required(stored.getType(), stored.getAmountCents()), checklistFor(claim));
        }
    }

    @Test
    void customerListOnlyContainsOwnClaimsAndTheirChecklists() throws Exception {
        List<Map<String, Object>> list = JsonPath.read(fetch("/api/claims", "cust-ben", "CUSTOMER"), "$");
        assertEquals(1, list.size());
        assertEquals("CLM-2001", list.get(0).get("id"));
        assertEquals(List.of("claim form", "photo of damage", "police report", "two repair estimates"),
                checklistFor(list.get(0)));
    }

    @Test
    void checklistIsDrivenByClaimAmountNotPayableAmount() throws Exception {
        String body = fetch("/api/claims/CLM-1001", "fin-1", "FINANCE");
        assertEquals(1_000_000, ((Number) JsonPath.read(body, "$.payableCents")).longValue());
        Map<String, Object> claim = claimOf(body);
        assertEquals(1_050_000, amountOf(claim));
        assertEquals(List.of("claim form", "photo of damage", "plumber or contractor report", "two repair estimates"),
                checklistFor(claim));
    }

    @Test
    void customerCannotLoadAnotherCustomersClaimDocuments() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimDetailWithoutGatewayHeadersIsUnauthorized() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001")).andExpect(status().isUnauthorized());
    }
}
