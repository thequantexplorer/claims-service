package com.northwind.claims.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CoverageCalculatorTest {
    private static final long DEDUCTIBLE = 50_000;
    private static final long LIMIT = 1_000_000;

    private static Claim claim(long cents) {
        return claim(cents, ClaimType.AUTO, ClaimStatus.APPROVED);
    }

    private static Claim claim(long cents, ClaimType type, ClaimStatus status) {
        LocalDate d = LocalDate.of(2026, 10, 1);
        return new Claim("C", "P", "cust", "Payee", type, status, cents, 1, d, d);
    }

    private static Policy policy(long deductible, long limit) {
        return new Policy("P", "cust", deductible, limit);
    }

    private static long payable(long claimCents) {
        return CoverageCalculator.payableCents(claim(claimCents), policy(DEDUCTIBLE, LIMIT));
    }

    @Test
    void subtractsTheDeductible() {
        assertEquals(250_000, CoverageCalculator.payableCents(claim(300_000), new Policy("P", "cust", 50_000, 1_000_000)));
    }

    @Test
    void claimOneCentBelowDeductiblePaysNothing() {
        assertEquals(0, payable(DEDUCTIBLE - 1));
    }

    @Test
    void claimExactlyAtDeductiblePaysNothing() {
        assertEquals(0, payable(DEDUCTIBLE));
    }

    @Test
    void claimOneCentAboveDeductiblePaysOneCent() {
        assertEquals(1, payable(DEDUCTIBLE + 1));
    }

    @Test
    void zeroClaimPaysNothing() {
        assertEquals(0, payable(0));
    }

    @Test
    void negativeClaimPaysNothing() {
        assertEquals(0, payable(-10_000));
    }

    @Test
    void amountAfterDeductibleOneCentBelowLimitIsPaidInFull() {
        assertEquals(LIMIT - 1, payable(DEDUCTIBLE + LIMIT - 1));
    }

    @Test
    void amountAfterDeductibleExactlyAtLimitPaysTheLimit() {
        assertEquals(LIMIT, payable(DEDUCTIBLE + LIMIT));
    }

    @Test
    void amountAfterDeductibleOneCentAboveLimitIsCappedAtLimit() {
        assertEquals(LIMIT, payable(DEDUCTIBLE + LIMIT + 1));
    }

    @Test
    void hugeClaimIsCappedAtLimit() {
        assertEquals(LIMIT, payable(1_000_000_000_000L));
    }

    @Test
    void capAppliesAfterDeductibleNotBefore() {
        // 1_020_000 - 50_000 = 970_000, below the 1_000_000 limit: deductible is taken before capping.
        assertEquals(970_000, payable(1_020_000));
    }

    @Test
    void zeroDeductiblePaysFullClaim() {
        assertEquals(123_456, CoverageCalculator.payableCents(claim(123_456), policy(0, LIMIT)));
    }

    @Test
    void zeroDeductibleZeroClaimPaysNothing() {
        assertEquals(0, CoverageCalculator.payableCents(claim(0), policy(0, LIMIT)));
    }

    @Test
    void zeroCoverageLimitPaysNothing() {
        assertEquals(0, CoverageCalculator.payableCents(claim(300_000), policy(DEDUCTIBLE, 0)));
    }

    @Test
    void seededPolicyPol1001WaterDamageClaim() {
        assertEquals(1_000_000, CoverageCalculator.payableCents(claim(1_050_000), policy(50_000, 2_500_000)));
    }

    @Test
    void seededPolicyPol2002TheftClaim() {
        assertEquals(2_000_000, CoverageCalculator.payableCents(claim(2_100_000), policy(100_000, 5_000_000)));
    }

    @Test
    void doesNotDependOnInstallments() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        Claim sixInstallments = new Claim("C", "P", "cust", "Payee", ClaimType.THEFT, ClaimStatus.APPROVED,
                300_000, 6, d, d);
        assertEquals(250_000, CoverageCalculator.payableCents(sixInstallments, policy(DEDUCTIBLE, LIMIT)));
    }

    @ParameterizedTest
    @EnumSource(ClaimType.class)
    void sameRuleForEveryClaimType(ClaimType type) {
        assertEquals(250_000, CoverageCalculator.payableCents(claim(300_000, type, ClaimStatus.APPROVED),
                policy(DEDUCTIBLE, LIMIT)));
        assertEquals(LIMIT, CoverageCalculator.payableCents(claim(5_000_000, type, ClaimStatus.APPROVED),
                policy(DEDUCTIBLE, LIMIT)));
    }

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    void sameRuleForEveryClaimStatus(ClaimStatus status) {
        assertEquals(250_000, CoverageCalculator.payableCents(claim(300_000, ClaimType.AUTO, status),
                policy(DEDUCTIBLE, LIMIT)));
    }

    @Test
    void doesNotMutateInputs() {
        Claim c = claim(300_000);
        Policy p = policy(DEDUCTIBLE, LIMIT);
        CoverageCalculator.payableCents(c, p);
        assertEquals(300_000, c.getAmountCents());
        assertEquals(DEDUCTIBLE, p.getDeductibleCents());
        assertEquals(LIMIT, p.getCoverageLimitCents());
    }
}
