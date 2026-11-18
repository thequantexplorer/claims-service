package com.northwind.claims.export;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PayoutExportApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void customerCanExportOwnClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001/payouts.csv").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void customerCannotExportAnotherCustomersClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001/payouts.csv").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void financeCanExportAnyClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001/payouts.csv").header("X-User-Id", "fin-1").header("X-User-Role", "FINANCE"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownClaimReturns404() throws Exception {
        mvc.perform(get("/api/claims/CLM-9999/payouts.csv").header("X-User-Id", "fin-1").header("X-User-Role", "FINANCE"))
                .andExpect(status().isNotFound());
    }
}
