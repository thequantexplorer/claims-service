package com.northwind.claims.export;

import com.northwind.claims.coverage.CoverageCalculator;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.Policy;
import com.northwind.claims.money.Money;
import java.time.LocalDate;

/** Builds the payout CSV that finance imports into the payments system. */
public final class PayoutExporter {
    private PayoutExporter() {}

    public static String toCsv(Claim claim, Policy policy, LocalDate firstPayment) {
        long payable = CoverageCalculator.payableCents(claim, policy);
        long perInstallment = Math.round((double) payable / claim.getInstallments());
        StringBuilder csv = new StringBuilder("claim_id,payee,installment,due_date,amount\n");
        for (int i = 0; i < claim.getInstallments(); i++) {
            csv.append(claim.getId()).append(',')
               .append(claim.getPayeeName()).append(',')
               .append(i + 1).append(',')
               .append(firstPayment.plusMonths(i)).append(',')
               .append(Money.format(perInstallment)).append('\n');
        }
        return csv.toString();
    }
}
