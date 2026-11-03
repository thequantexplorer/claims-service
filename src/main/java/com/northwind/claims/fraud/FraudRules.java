package com.northwind.claims.fraud;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.time.temporal.ChronoUnit;

public final class FraudRules {
    public static final int REVIEW_THRESHOLD = 50;

    private FraudRules() {}

    /** A 0-100 risk score. Claims at or above REVIEW_THRESHOLD go to the special investigations unit. */
    public static int score(Claim claim, Policy policy, int priorClaimsThisYear) {
        int score = 0;
        if (claim.getAmountCents() >= policy.getCoverageLimitCents() * 9 / 10) {
            score += 30;
        }
        if (ChronoUnit.DAYS.between(claim.getIncidentDate(), claim.getReportedDate()) > 30) {
            score += 20;
        }
        if (priorClaimsThisYear >= 2) {
            score += 25;
        }
        if (claim.getType() == ClaimType.THEFT) {
            score += 15;
        }
        return Math.min(score, 100);
    }

    public static boolean needsInvestigation(int score) {
        return score >= REVIEW_THRESHOLD;
    }
}
