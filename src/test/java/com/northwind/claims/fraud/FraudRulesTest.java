package com.northwind.claims.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FraudRulesTest {
    private static final LocalDate INCIDENT = LocalDate.of(2026, 10, 1);
    private static final Policy POLICY = new Policy("P", "cust", 50_000, 1_000_000);

    private static Claim claim(ClaimType type, long cents, int reportedAfterDays) {
        return new Claim("C", "P", "cust", "Payee", type, ClaimStatus.SUBMITTED, cents, 1,
                INCIDENT, INCIDENT.plusDays(reportedAfterDays));
    }

    private static Claim lowRisk() {
        return claim(ClaimType.AUTO, 100_000, 1);
    }

    @Test
    void lowRiskClaimScoresZero() {
        assertEquals(0, FraudRules.score(lowRisk(), POLICY, 0));
    }

    @Test
    void amountAtNinetyPercentOfLimitAddsThirty() {
        assertEquals(30, FraudRules.score(claim(ClaimType.AUTO, 900_000, 1), POLICY, 0));
    }

    @Test
    void amountJustBelowNinetyPercentOfLimitAddsNothing() {
        assertEquals(0, FraudRules.score(claim(ClaimType.AUTO, 899_999, 1), POLICY, 0));
    }

    @Test
    void amountAboveTheLimitAddsThirty() {
        assertEquals(30, FraudRules.score(claim(ClaimType.AUTO, 2_000_000, 1), POLICY, 0));
    }

    @Test
    void reportedMoreThanThirtyDaysLateAddsTwenty() {
        assertEquals(20, FraudRules.score(claim(ClaimType.AUTO, 100_000, 31), POLICY, 0));
    }

    @Test
    void reportedExactlyThirtyDaysLaterAddsNothing() {
        assertEquals(0, FraudRules.score(claim(ClaimType.AUTO, 100_000, 30), POLICY, 0));
    }

    @Test
    void twoPriorClaimsThisYearAddTwentyFive() {
        assertEquals(25, FraudRules.score(lowRisk(), POLICY, 2));
    }

    @Test
    void onePriorClaimThisYearAddsNothing() {
        assertEquals(0, FraudRules.score(lowRisk(), POLICY, 1));
    }

    @Test
    void theftAddsFifteen() {
        assertEquals(15, FraudRules.score(claim(ClaimType.THEFT, 100_000, 1), POLICY, 0));
    }

    @ParameterizedTest
    @EnumSource(value = ClaimType.class, names = "THEFT", mode = EnumSource.Mode.EXCLUDE)
    void nonTheftTypesAddNothing(ClaimType type) {
        assertEquals(0, FraudRules.score(claim(type, 100_000, 1), POLICY, 0));
    }

    @Test
    void factorsAddUp() {
        assertEquals(50, FraudRules.score(claim(ClaimType.AUTO, 950_000, 45), POLICY, 0));
        assertEquals(90, FraudRules.score(claim(ClaimType.THEFT, 950_000, 45), POLICY, 5));
    }

    @Test
    void scoreStaysWithinZeroToOneHundred() {
        int score = FraudRules.score(claim(ClaimType.THEFT, Long.MAX_VALUE / 100, 365), POLICY, 100);
        assertTrue(score >= 0 && score <= 100, "score " + score);
    }

    @Test
    void investigationThresholdIsInclusive() {
        assertFalse(FraudRules.needsInvestigation(FraudRules.REVIEW_THRESHOLD - 1));
        assertTrue(FraudRules.needsInvestigation(FraudRules.REVIEW_THRESHOLD));
        assertTrue(FraudRules.needsInvestigation(100));
        assertFalse(FraudRules.needsInvestigation(0));
    }

    @Test
    void highAmountPlusLateReportGoesToInvestigation() {
        int score = FraudRules.score(claim(ClaimType.AUTO, 950_000, 45), POLICY, 0);
        assertTrue(FraudRules.needsInvestigation(score));
    }

    @Test
    void theftWithPriorClaimsStaysBelowThreshold() {
        int score = FraudRules.score(claim(ClaimType.THEFT, 100_000, 1), POLICY, 2);
        assertEquals(40, score);
        assertFalse(FraudRules.needsInvestigation(score));
    }
}
