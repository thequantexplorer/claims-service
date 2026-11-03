package com.northwind.claims.payout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import com.northwind.claims.payout.PayoutCalculator.Installment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayoutCalculatorTest {
    private static final LocalDate FIRST = LocalDate.of(2026, 10, 1);

    private static Claim claim(long cents, int installments) {
        return new Claim("C", "P", "cust", "Payee", ClaimType.AUTO, ClaimStatus.APPROVED, cents, installments, FIRST, FIRST);
    }

    private static Policy policy(long deductibleCents, long coverageLimitCents) {
        return new Policy("P", "cust", deductibleCents, coverageLimitCents);
    }

    @Test
    void payableSubtractsTheDeductible() {
        assertEquals(250_000, PayoutCalculator.payableCents(claim(300_000, 1), policy(50_000, 1_000_000)));
    }

    @Test
    void payableIsCappedAtTheCoverageLimit() {
        assertEquals(1_000_000, PayoutCalculator.payableCents(claim(5_000_000, 1), policy(50_000, 1_000_000)));
    }

    @Test
    void payableIsZeroWhenTheDeductibleCoversTheClaim() {
        assertEquals(0, PayoutCalculator.payableCents(claim(50_000, 1), policy(50_000, 1_000_000)));
        assertEquals(0, PayoutCalculator.payableCents(claim(10_000, 1), policy(50_000, 1_000_000)));
    }

    @Test
    void scheduleSpreadsLeftoverCentsAndAddsUpToTheTotal() {
        List<Installment> schedule = PayoutCalculator.schedule(1_000_000, 3, FIRST);
        assertEquals(List.of(
                new Installment(1, LocalDate.of(2026, 10, 1), 333_334),
                new Installment(2, LocalDate.of(2026, 11, 1), 333_333),
                new Installment(3, LocalDate.of(2026, 12, 1), 333_333)), schedule);
        assertEquals(1_000_000, schedule.stream().mapToLong(Installment::amountCents).sum());
    }

    @Test
    void scheduleDueDatesAreMonthlyFromTheFirstPayment() {
        List<Installment> schedule = PayoutCalculator.schedule(1_200, 12, LocalDate.of(2026, 1, 31));
        assertEquals(LocalDate.of(2026, 2, 28), schedule.get(1).dueDate());
        assertEquals(LocalDate.of(2026, 12, 31), schedule.get(11).dueDate());
    }

    @Test
    void scheduleRejectsNonPositiveInstallments() {
        assertThrows(IllegalArgumentException.class, () -> PayoutCalculator.schedule(1_000, 0, FIRST));
    }

    @Test
    void claimScheduleUsesPayableAmountAndInstallmentCount() {
        List<Installment> schedule = PayoutCalculator.schedule(claim(1_050_000, 3), policy(50_000, 2_500_000), FIRST);
        assertEquals(List.of(333_334L, 333_333L, 333_333L), schedule.stream().map(Installment::amountCents).toList());
    }
}
