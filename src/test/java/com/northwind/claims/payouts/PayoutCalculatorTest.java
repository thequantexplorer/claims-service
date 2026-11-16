package com.northwind.claims.payouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import com.northwind.claims.payouts.PayoutCalculator.Installment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayoutCalculatorTest {
    private static final LocalDate D = LocalDate.of(2026, 10, 1);
    private static final Policy POLICY = new Policy("P", "cust", 50_000, 1_000_000);

    private static Claim claim(long cents, int installments) {
        return new Claim("C", "P", "cust", "Payee", ClaimType.AUTO, ClaimStatus.APPROVED, cents, installments, D, D);
    }

    @Test
    void subtractsTheDeductible() {
        assertEquals(250_000, PayoutCalculator.payableCents(claim(300_000, 1), POLICY));
    }

    @Test
    void paysNothingWhenTheClaimDoesNotExceedTheDeductible() {
        assertEquals(0, PayoutCalculator.payableCents(claim(50_000, 1), POLICY));
        assertEquals(0, PayoutCalculator.payableCents(claim(20_000, 1), POLICY));
    }

    @Test
    void capsAtTheCoverageLimit() {
        assertEquals(1_000_000, PayoutCalculator.payableCents(claim(1_050_000, 1), POLICY));
        assertEquals(1_000_000, PayoutCalculator.payableCents(claim(5_000_000, 1), POLICY));
    }

    @Test
    void scheduleIsMonthlyWithRemainderCentsOnTheEarliestInstallments() {
        assertEquals(List.of(
                new Installment(1, LocalDate.of(2026, 10, 1), 333_334),
                new Installment(2, LocalDate.of(2026, 11, 1), 333_333),
                new Installment(3, LocalDate.of(2026, 12, 1), 333_333)),
                PayoutCalculator.schedule(1_000_000, 3, D));
    }

    @Test
    void scheduleAddsBackUpToThePayableAmount() {
        long total = PayoutCalculator.schedule(1_000_001, 7, D).stream().mapToLong(Installment::amountCents).sum();
        assertEquals(1_000_001, total);
    }

    @Test
    void dueDatesAreCountedFromTheFirstPaymentAtMonthEnd() {
        List<LocalDate> due = PayoutCalculator.schedule(300, 3, LocalDate.of(2027, 1, 31)).stream()
                .map(Installment::dueDate).toList();
        assertEquals(List.of(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)), due);
    }

    @Test
    void scheduleRejectsNonPositiveInstallments() {
        assertThrows(IllegalArgumentException.class, () -> PayoutCalculator.schedule(100, 0, D));
    }

    @Test
    void scheduleForAClaimSplitsItsPayableAmountOverItsInstallments() {
        assertEquals(PayoutCalculator.schedule(1_000_000, 3, D),
                PayoutCalculator.schedule(claim(1_050_000, 3), POLICY, D));
    }
}
