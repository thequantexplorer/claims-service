package com.northwind.claims.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.ClaimStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationsApiIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ClaimRepository claims;

    private String fetch(String id, String user, String role) throws Exception {
        return mvc.perform(get("/api/claims/" + id).header("X-User-Id", user).header("X-User-Role", role))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String emailFor(String body) {
        String id = JsonPath.read(body, "$.claim.id");
        long payable = ((Number) JsonPath.read(body, "$.payableCents")).longValue();
        Claim claim = claims.findById(id).orElseThrow();
        assertEquals(ClaimStatus.valueOf(JsonPath.read(body, "$.claim.status")), claim.getStatus());
        return NotificationTemplates.statusEmail(claim, payable);
    }

    @Test
    void approvedClaimEmailForOwningCustomer() throws Exception {
        String body = fetch("CLM-1001", "cust-ana", "CUSTOMER");
        assertEquals(1_000_000L, ((Number) JsonPath.read(body, "$.payableCents")).longValue());
        assertEquals(3, (int) JsonPath.read(body, "$.claim.installments"));
        assertEquals("Claim CLM-1001 is approved. We will pay $10000.00 in 3 installment(s).", emailFor(body));
    }

    @Test
    void inReviewClaimEmailForOwningCustomer() throws Exception {
        String body = fetch("CLM-1002", "cust-ana", "CUSTOMER");
        assertEquals("IN_REVIEW", JsonPath.read(body, "$.claim.status"));
        assertEquals("An adjuster is reviewing claim CLM-1002.", emailFor(body));
        assertFalse(NotificationTemplates.isFinal(ClaimStatus.IN_REVIEW));
    }

    @Test
    void approvedClaimEmailForAdjuster() throws Exception {
        String body = fetch("CLM-2001", "adj-1", "ADJUSTER");
        assertEquals(2_000_000L, ((Number) JsonPath.read(body, "$.payableCents")).longValue());
        assertEquals("Claim CLM-2001 is approved. We will pay $20000.00 in 6 installment(s).", emailFor(body));
    }

    @Test
    void approvedClaimEmailForFinanceMatchesCustomerView() throws Exception {
        String financeEmail = emailFor(fetch("CLM-2001", "fin-1", "FINANCE"));
        String customerEmail = emailFor(fetch("CLM-2001", "cust-ben", "CUSTOMER"));
        assertEquals("Claim CLM-2001 is approved. We will pay $20000.00 in 6 installment(s).", financeEmail);
        assertEquals(financeEmail, customerEmail);
    }

    @Test
    void seededClaimsAreNotFinal() throws Exception {
        String body = mvc.perform(get("/api/claims").header("X-User-Id", "adj-1").header("X-User-Role", "ADJUSTER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<String> statuses = JsonPath.read(body, "$[*].status");
        assertEquals(3, statuses.size());
        for (String s : statuses) {
            assertFalse(NotificationTemplates.isFinal(ClaimStatus.valueOf(s)), s);
        }
    }

    @Test
    void customerCannotFetchAnotherCustomersClaimForNotification() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimRequestWithoutHeadersIsUnauthorized() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001")).andExpect(status().isUnauthorized());
    }
}
