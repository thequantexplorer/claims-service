package com.northwind.claims.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PayoutExporterTest {
    @Test
    void installmentsAddUpToThePayableAmount() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        Claim claim = new Claim("CLM-1001", "POL-1001", "cust-ana", "Ana Ruiz", ClaimType.WATER_DAMAGE,
                ClaimStatus.APPROVED, 1_050_000, 3, d, d);
        Policy policy = new Policy("POL-1001", "cust-ana", 50_000, 2_500_000);
        String csv = PayoutExporter.toCsv(claim, policy, d);
        BigDecimal sum = Arrays.stream(csv.split("\n")).skip(1)
                .map(line -> new BigDecimal(line.substring(line.lastIndexOf(',') + 1)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("10000.00"), sum);
    }

    @Test
    void leftoverCentsAreDistributedAcrossInstallments() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        Claim claim = new Claim("CLM-1001", "POL-1001", "cust-ana", "Ana Ruiz", ClaimType.WATER_DAMAGE,
                ClaimStatus.APPROVED, 1_050_000, 3, d, d);
        Policy policy = new Policy("POL-1001", "cust-ana", 50_000, 2_500_000);
        String[] amounts = Arrays.stream(PayoutExporter.toCsv(claim, policy, d).split("\n")).skip(1)
                .map(line -> line.substring(line.lastIndexOf(',') + 1))
                .toArray(String[]::new);
        assertArrayEquals(new String[] {"3333.34", "3333.33", "3333.33"}, amounts);
    }

    @Test
    void payeeWithCommaQuoteOrNewlineIsQuoted() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        Claim claim = new Claim("CLM-1001", "POL-1001", "cust-ana", "Ruiz, Ana \"Annie\"\nJr", ClaimType.WATER_DAMAGE,
                ClaimStatus.APPROVED, 150_000, 1, d, d);
        Policy policy = new Policy("POL-1001", "cust-ana", 50_000, 2_500_000);
        String csv = PayoutExporter.toCsv(claim, policy, d);
        assertEquals("claim_id,payee,installment,due_date,amount\n"
                + "CLM-1001,\"Ruiz, Ana \"\"Annie\"\"\nJr\",1,2026-10-01,1000.00\n", csv);
    }

    @Test
    void plainPayeeIsNotQuoted() {
        assertEquals("Ana Ruiz", PayoutExporter.csvField("Ana Ruiz"));
    }
}
