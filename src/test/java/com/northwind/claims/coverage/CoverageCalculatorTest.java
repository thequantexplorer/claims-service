package com.northwind.claims.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoverageCalculatorTest {
    private static Claim claim(long cents) {
        LocalDate d = LocalDate.of(2026, 10, 1);
        return new Claim("C", "P", "cust", "Payee", ClaimType.AUTO, ClaimStatus.APPROVED, cents, 1, d, d);
    }

    private static Policy policy(long deductibleCents, long limitCents) {
        return new Policy("P", "cust", deductibleCents, limitCents);
    }

    @Test
    void subtractsTheDeductible() {
        assertEquals(250_000, CoverageCalculator.payableCents(claim(300_000), policy(50_000, 1_000_000)));
    }

    @Test
    void claimEqualToDeductiblePaysNothing() {
        assertEquals(0, CoverageCalculator.payableCents(claim(50_000), policy(50_000, 1_000_000)));
    }

    @Test
    void claimBelowDeductiblePaysNothing() {
        assertEquals(0, CoverageCalculator.payableCents(claim(10_000), policy(50_000, 1_000_000)));
    }

    @Test
    void oneCentOverDeductiblePaysOneCent() {
        assertEquals(1, CoverageCalculator.payableCents(claim(50_001), policy(50_000, 1_000_000)));
    }

    @Test
    void zeroDeductiblePaysTheFullClaim() {
        assertEquals(123_456, CoverageCalculator.payableCents(claim(123_456), policy(0, 1_000_000)));
    }

    @Test
    void capsAtTheCoverageLimit() {
        assertEquals(1_000_000, CoverageCalculator.payableCents(claim(5_000_000), policy(50_000, 1_000_000)));
    }

    @Test
    void amountAfterDeductibleExactlyAtLimitIsPaidInFull() {
        assertEquals(1_000_000, CoverageCalculator.payableCents(claim(1_050_000), policy(50_000, 1_000_000)));
    }

    @Test
    void zeroAmountClaimPaysNothing() {
        assertEquals(0, CoverageCalculator.payableCents(claim(0), policy(50_000, 1_000_000)));
    }

    @ParameterizedTest(name = "{0} cents, deductible {1}, limit {2} -> {3}")
    @CsvSource({
            "1050000, 50000, 2500000, 1000000",
            "320000, 50000, 2500000, 270000",
            "2100000, 100000, 5000000, 2000000",
            "9999999, 100000, 5000000, 5000000",
    })
    void payableIsNeverNegativeNorAboveTheLimit(long amount, long deductible, long limit, long expected) {
        long payable = CoverageCalculator.payableCents(claim(amount), policy(deductible, limit));
        assertEquals(expected, payable);
    }
}
