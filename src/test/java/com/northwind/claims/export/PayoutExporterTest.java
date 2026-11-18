package com.northwind.claims.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PayoutExporterTest {
    private static final LocalDate D = LocalDate.of(2026, 10, 1);
    private static final Policy POLICY = new Policy("POL-1001", "cust-ana", 50_000, 2_500_000);

    private static Claim claim(String payee, long amountCents, int installments) {
        return new Claim("CLM-1001", "POL-1001", "cust-ana", payee, ClaimType.WATER_DAMAGE,
                ClaimStatus.APPROVED, amountCents, installments, D, D);
    }

    private static List<String> rows(String csv) {
        return Arrays.stream(csv.split("\n")).skip(1).toList();
    }

    @Test
    void installmentsAddUpToThePayableAmount() {
        String csv = PayoutExporter.toCsv(claim("Ana Ruiz", 1_050_000, 3), POLICY, D);
        BigDecimal sum = rows(csv).stream()
                .map(line -> new BigDecimal(line.substring(line.lastIndexOf(',') + 1)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("10000.00"), sum);
    }

    @Test
    void remainderCentsGoToTheEarliestInstallments() {
        String csv = PayoutExporter.toCsv(claim("Ana Ruiz", 1_050_000, 3), POLICY, D);
        assertEquals(List.of(
                "CLM-1001,Ana Ruiz,1,2026-10-01,3333.34",
                "CLM-1001,Ana Ruiz,2,2026-11-01,3333.33",
                "CLM-1001,Ana Ruiz,3,2026-12-01,3333.33"), rows(csv));
    }

    @Test
    void payeeWithCommaIsQuoted() {
        String csv = PayoutExporter.toCsv(claim("Smith, Jr.", 60_000, 1), POLICY, D);
        assertEquals(List.of("CLM-1001,\"Smith, Jr.\",1,2026-10-01,100.00"), rows(csv));
    }

    @Test
    void payeeWithQuotesHasQuotesDoubled() {
        String csv = PayoutExporter.toCsv(claim("Ann \"Annie\" Lee", 60_000, 1), POLICY, D);
        assertEquals(List.of("CLM-1001,\"Ann \"\"Annie\"\" Lee\",1,2026-10-01,100.00"), rows(csv));
    }

    @Test
    void payeeWithLineBreakStaysInsideOneQuotedField() {
        String csv = PayoutExporter.toCsv(claim("Ana\nRuiz", 60_000, 1), POLICY, D);
        assertEquals("claim_id,payee,installment,due_date,amount\n"
                + "CLM-1001,\"Ana\nRuiz\",1,2026-10-01,100.00\n", csv);
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+2", "+1+2", "-1+2", "@SUM(A1)", "\t=1+2", "\r=1+2"})
    void payeeThatLooksLikeAFormulaIsNeutralized(String payee) {
        String csv = PayoutExporter.toCsv(claim(payee, 60_000, 1), POLICY, D);
        String row = csv.substring(csv.indexOf("\n") + 1);
        String payeeCell = row.substring("CLM-1001,".length());
        char first = payeeCell.charAt(0) == '"' ? payeeCell.charAt(1) : payeeCell.charAt(0);
        assertEquals('\'', first, "payee cell must not start with a formula trigger: " + row);
    }

    @Test
    void formulaPayeeWithCommaIsNeutralizedAndQuoted() {
        String csv = PayoutExporter.toCsv(claim("=HYPERLINK(\"http://x\",\"pay\")", 60_000, 1), POLICY, D);
        assertEquals(List.of("CLM-1001,\"'=HYPERLINK(\"\"http://x\"\",\"\"pay\"\")\",1,2026-10-01,100.00"), rows(csv));
    }
}
