package com.northwind.claims.export;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
class PayoutExportApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ClaimRepository claims;

    @Test
    void financeExportsApprovedClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001/payouts.csv").header("X-User-Id", "fin-1").header("X-User-Role", "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith(
                        "claim_id,payee,installment,due_date,amount\nCLM-1001,Ana Ruiz,1,")));
    }

    @Test
    void customerExportsOwnApprovedClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001/payouts.csv").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotExportAnotherCustomersClaim() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001/payouts.csv").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownClaimIsNotFound() throws Exception {
        mvc.perform(get("/api/claims/CLM-9999/payouts.csv").header("X-User-Id", "fin-1").header("X-User-Role", "FINANCE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void claimWithMissingPolicyIsNotFound() throws Exception {
        LocalDate d = LocalDate.of(2026, 10, 1);
        claims.save(new Claim("CLM-NOPOL", "POL-MISSING", "cust-zed", "Zed", ClaimType.AUTO,
                ClaimStatus.APPROVED, 500_000, 1, d, d));
        mvc.perform(get("/api/claims/CLM-NOPOL/payouts.csv").header("X-User-Id", "fin-1").header("X-User-Role", "FINANCE"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    @Transactional
    void claimThatIsNotApprovedHasNoPayoutExport(ClaimStatus status) throws Exception {
        LocalDate d = LocalDate.of(2026, 10, 1);
        claims.save(new Claim("CLM-" + status, "POL-1001", "cust-ana", "Ana Ruiz", ClaimType.AUTO,
                status, 320_000, 1, d, d));
        mvc.perform(get("/api/claims/CLM-" + status + "/payouts.csv").header("X-User-Id", "fin-1").header("X-User-Role", "FINANCE"))
                .andExpect(status().isConflict());
    }

    @Test
    void seededInReviewClaimHasNoPayoutExport() throws Exception {
        mvc.perform(get("/api/claims/CLM-1002/payouts.csv").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isConflict());
    }
}
