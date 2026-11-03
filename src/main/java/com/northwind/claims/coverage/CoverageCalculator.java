package com.northwind.claims.coverage;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.Policy;

public final class CoverageCalculator {
    private CoverageCalculator() {}

    /** Amount the insurer pays: the claim minus the deductible, capped at the coverage limit. */
    public static long payableCents(Claim claim, Policy policy) {
        long afterDeductible = claim.getAmountCents() - policy.getDeductibleCents();
        if (afterDeductible <= 0) {
            return 0;
        }
        return Math.min(afterDeductible, policy.getCoverageLimitCents());
    }
}
