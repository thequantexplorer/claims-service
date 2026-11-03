package com.northwind.claims.export;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.Policy;
import com.northwind.claims.money.Money;
import com.northwind.claims.payout.PayoutCalculator;
import java.time.LocalDate;

/** Builds the payout CSV that finance imports into the payments system. */
public final class PayoutExporter {
    private PayoutExporter() {}

    public static String toCsv(Claim claim, Policy policy, LocalDate firstPayment) {
        StringBuilder csv = new StringBuilder("claim_id,payee,installment,due_date,amount\n");
        for (PayoutCalculator.Installment installment : PayoutCalculator.schedule(claim, policy, firstPayment)) {
            csv.append(csvField(claim.getId())).append(',')
               .append(csvField(claim.getPayeeName())).append(',')
               .append(installment.number()).append(',')
               .append(installment.dueDate()).append(',')
               .append(Money.format(installment.amountCents())).append('\n');
        }
        return csv.toString();
    }

    /** RFC 4180: quote fields containing a comma, quote or line break, doubling embedded quotes. */
    static String csvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
