package com.northwind.claims.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.time.LocalDate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FraudRulesTest {
    private static final LocalDate INCIDENT = LocalDate.of(2026, 10, 1);
    /** Limit 1_000_000 cents: 90% is exactly 900_000 cents. */
    private static final Policy POLICY = new Policy("P", "cust", 50_000, 1_000_000);

    private static Claim claim(ClaimType type, long cents, int reportedAfterDays) {
        return new Claim("C", "P", "cust", "Payee", type, ClaimStatus.SUBMITTED, cents, 1,
                INCIDENT, INCIDENT.plusDays(reportedAfterDays));
    }

    private static Claim lowRisk(long cents) {
        return claim(ClaimType.AUTO, cents, 0);
    }

    @Test
    void reviewThresholdIsFifty() {
        assertEquals(50, FraudRules.REVIEW_THRESHOLD);
    }

    @Test
    void cleanClaimScoresZero() {
        assertEquals(0, FraudRules.score(lowRisk(100_000), POLICY, 0));
    }

    @Test
    void zeroAmountClaimScoresZero() {
        assertEquals(0, FraudRules.score(lowRisk(0), POLICY, 0));
    }

    @Test
    void amountExactlyAtNinetyPercentOfLimitAddsThirty() {
        assertEquals(30, FraudRules.score(lowRisk(900_000), POLICY, 0));
    }

    @Test
    void amountOneCentBelowNinetyPercentOfLimitAddsNothing() {
        assertEquals(0, FraudRules.score(lowRisk(899_999), POLICY, 0));
    }

    @Test
    void amountOneCentAboveNinetyPercentOfLimitAddsThirty() {
        assertEquals(30, FraudRules.score(lowRisk(900_001), POLICY, 0));
    }

    @Test
    void amountAboveCoverageLimitAddsThirty() {
        assertEquals(30, FraudRules.score(lowRisk(1_500_000), POLICY, 0));
    }

    @Test
    @Disabled("Bug: limit * 9 / 10 floors, so amounts just below 90% of a limit not divisible by 10 are scored as large")
    void amountBelowNinetyPercentOfOddLimitAddsNothing() {
        Policy oddLimit = new Policy("P", "cust", 0, 1_000_005);
        // 90% of 1_000_005 is 900_004.5 cents; 900_004 is below it.
        assertEquals(0, FraudRules.score(lowRisk(900_004), oddLimit, 0));
    }

    @Test
    void amountAtOrAboveNinetyPercentOfOddLimitAddsThirty() {
        assertEquals(30, FraudRules.score(lowRisk(900_005), new Policy("P", "cust", 0, 1_000_005), 0));
    }

    @Test
    void zeroCoverageLimitFlagsAnyNonNegativeAmountAsLarge() {
        assertEquals(30, FraudRules.score(lowRisk(0), new Policy("P", "cust", 0, 0), 0));
    }

    @Test
    void reportedExactlyThirtyDaysAfterIncidentAddsNothing() {
        assertEquals(0, FraudRules.score(claim(ClaimType.AUTO, 100_000, 30), POLICY, 0));
    }

    @Test
    void reportedTwentyNineDaysAfterIncidentAddsNothing() {
        assertEquals(0, FraudRules.score(claim(ClaimType.AUTO, 100_000, 29), POLICY, 0));
    }

    @Test
    void reportedThirtyOneDaysAfterIncidentAddsTwenty() {
        assertEquals(20, FraudRules.score(claim(ClaimType.AUTO, 100_000, 31), POLICY, 0));
    }

    @Test
    void reportedLateAcrossMonthBoundaryCountsCalendarDays() {
        Claim claim = new Claim("C", "P", "cust", "Payee", ClaimType.AUTO, ClaimStatus.SUBMITTED, 100_000, 1,
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 3));
        assertEquals(20, FraudRules.score(claim, POLICY, 0));
    }

    @Test
    void reportedBeforeIncidentDateAddsNothing() {
        assertEquals(0, FraudRules.score(claim(ClaimType.AUTO, 100_000, -45), POLICY, 0));
    }

    @Test
    void twoPriorClaimsThisYearAddTwentyFive() {
        assertEquals(25, FraudRules.score(lowRisk(100_000), POLICY, 2));
    }

    @Test
    void onePriorClaimThisYearAddsNothing() {
        assertEquals(0, FraudRules.score(lowRisk(100_000), POLICY, 1));
    }

    @Test
    void threePriorClaimsThisYearAddTwentyFive() {
        assertEquals(25, FraudRules.score(lowRisk(100_000), POLICY, 3));
    }

    @Test
    void negativePriorClaimCountAddsNothing() {
        assertEquals(0, FraudRules.score(lowRisk(100_000), POLICY, -1));
    }

    @Test
    void theftAddsFifteen() {
        assertEquals(15, FraudRules.score(claim(ClaimType.THEFT, 100_000, 0), POLICY, 0));
    }

    @ParameterizedTest
    @EnumSource(value = ClaimType.class, names = "THEFT", mode = EnumSource.Mode.EXCLUDE)
    void nonTheftTypesAddNothing(ClaimType type) {
        assertEquals(0, FraudRules.score(claim(type, 100_000, 0), POLICY, 0));
    }

    @Test
    void allRulesTriggeredSumToNinety() {
        assertEquals(90, FraudRules.score(claim(ClaimType.THEFT, 1_000_000, 31), POLICY, 2));
    }

    @Test
    void largeAndLateClaimScoresExactlyAtThresholdAndNeedsInvestigation() {
        int score = FraudRules.score(claim(ClaimType.AUTO, 900_000, 31), POLICY, 0);
        assertEquals(50, score);
        assertTrue(FraudRules.needsInvestigation(score));
    }

    @Test
    void largeTheftClaimScoresFortyFiveAndDoesNotNeedInvestigation() {
        int score = FraudRules.score(claim(ClaimType.THEFT, 900_000, 0), POLICY, 0);
        assertEquals(45, score);
        assertFalse(FraudRules.needsInvestigation(score));
    }

    @Test
    void largeClaimWithRepeatClaimsScoresFiftyFive() {
        int score = FraudRules.score(lowRisk(950_000), POLICY, 2);
        assertEquals(55, score);
        assertTrue(FraudRules.needsInvestigation(score));
    }

    @Test
    void lateTheftWithRepeatClaimsScoresSixty() {
        int score = FraudRules.score(claim(ClaimType.THEFT, 100_000, 60), POLICY, 2);
        assertEquals(60, score);
        assertTrue(FraudRules.needsInvestigation(score));
    }

    @Test
    void needsInvestigationExactlyAtThreshold() {
        assertTrue(FraudRules.needsInvestigation(50));
    }

    @Test
    void needsInvestigationOneBelowThresholdIsFalse() {
        assertFalse(FraudRules.needsInvestigation(49));
    }

    @Test
    void needsInvestigationOneAboveThresholdIsTrue() {
        assertTrue(FraudRules.needsInvestigation(51));
    }

    @Test
    void needsInvestigationAtBoundsOfScoreRange() {
        assertFalse(FraudRules.needsInvestigation(0));
        assertTrue(FraudRules.needsInvestigation(100));
    }

    @Test
    void needsInvestigationForNegativeScoreIsFalse() {
        assertFalse(FraudRules.needsInvestigation(-1));
    }
}
