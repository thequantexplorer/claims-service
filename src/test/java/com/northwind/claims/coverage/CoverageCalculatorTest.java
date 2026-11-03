package com.northwind.claims.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CoverageCalculatorTest {
    private static Claim claim(long cents) {
        LocalDate d = LocalDate.of(2026, 10, 1);
        return new Claim("C", "P", "cust", "Payee", ClaimType.AUTO, ClaimStatus.APPROVED, cents, 1, d, d);
    }

    @Test
    void subtractsTheDeductible() {
        assertEquals(250_000, CoverageCalculator.payableCents(claim(300_000), new Policy("P", "cust", 50_000, 1_000_000)));
    }
}
