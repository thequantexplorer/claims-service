package com.northwind.claims.payouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.northwind.claims.coverage.CoverageCalculator;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.Policy;
import com.northwind.claims.model.PolicyRepository;
import com.northwind.claims.payouts.PayoutSchedule.Installment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PayoutsApiIntegrationTest {
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

    private static List<Installment> scheduleFrom(String json) {
        long payable = ((Number) JsonPath.read(json, "$.payableCents")).longValue();
        int installments = JsonPath.read(json, "$.claim.installments");
        LocalDate reported = LocalDate.parse(JsonPath.read(json, "$.claim.reportedDate"));
        return PayoutSchedule.build(payable, installments, reported);
    }

    private static List<Long> amounts(List<Installment> schedule) {
        return schedule.stream().map(Installment::amountCents).toList();
    }

    @Test
    void customerOwnApprovedClaimSchedulesThreeInstallmentsThatAddUpToPayable() throws Exception {
        String json = fetch("/api/claims/CLM-1001", "cust-ana", "CUSTOMER");

        List<Installment> schedule = scheduleFrom(json);

        assertEquals(List.of(
                new Installment(1, LocalDate.of(2026, 10, 3), 333_334),
                new Installment(2, LocalDate.of(2026, 11, 3), 333_333),
                new Installment(3, LocalDate.of(2026, 12, 3), 333_333)), schedule);
        assertEquals(1_000_000, schedule.stream().mapToLong(Installment::amountCents).sum());
    }

    @Test
    void adjusterSeesSixInstallmentScheduleForAnotherCustomersClaim() throws Exception {
        String json = fetch("/api/claims/CLM-2001", "adj-1", "ADJUSTER");

        List<Installment> schedule = scheduleFrom(json);

        assertEquals(List.of(333_334L, 333_334L, 333_333L, 333_333L, 333_333L, 333_333L), amounts(schedule));
        assertEquals(2_000_000, schedule.stream().mapToLong(Installment::amountCents).sum());
        assertEquals(LocalDate.of(2026, 10, 10), schedule.get(0).dueDate());
        assertEquals(LocalDate.of(2027, 3, 10), schedule.get(5).dueDate());
    }

    @Test
    void financeSeesSingleInstallmentForInReviewClaim() throws Exception {
        String json = fetch("/api/claims/CLM-1002", "fin-1", "FINANCE");

        assertEquals(List.of(new Installment(1, LocalDate.of(2026, 10, 12), 270_000)), scheduleFrom(json));
    }

    @Test
    void scheduleFromApiMatchesScheduleBuiltFromRepositoryData() throws Exception {
        String json = fetch("/api/claims/CLM-2001", "cust-ben", "CUSTOMER");

        Claim claim = claims.findById("CLM-2001").orElseThrow();
        Policy policy = policies.findById(claim.getPolicyId()).orElseThrow();
        List<Installment> fromRepository = PayoutSchedule.build(
                CoverageCalculator.payableCents(claim, policy), claim.getInstallments(), claim.getReportedDate());

        assertEquals(fromRepository, scheduleFrom(json));
    }

    @Test
    void everySeededClaimListedForFinanceHasAScheduleThatAddsUpToItsPayable() throws Exception {
        String list = fetch("/api/claims", "fin-1", "FINANCE");
        List<String> ids = JsonPath.read(list, "$[*].id");

        assertEquals(List.of("CLM-1001", "CLM-1002", "CLM-2001"), ids.stream().sorted().toList());
        for (String id : ids) {
            String json = fetch("/api/claims/" + id, "fin-1", "FINANCE");
            long payable = ((Number) JsonPath.read(json, "$.payableCents")).longValue();
            int installments = JsonPath.read(json, "$.claim.installments");
            List<Installment> schedule = scheduleFrom(json);

            assertEquals(installments, schedule.size(), id);
            assertEquals(payable, schedule.stream().mapToLong(Installment::amountCents).sum(), id);
        }
    }

    @Test
    void customerCannotSeeAnotherCustomersClaimToBuildItsSchedule() throws Exception {
        mvc.perform(get("/api/claims/CLM-2001").header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimDetailWithoutGatewayHeadersIsRejected() throws Exception {
        mvc.perform(get("/api/claims/CLM-1001")).andExpect(status().isUnauthorized());
    }
}
