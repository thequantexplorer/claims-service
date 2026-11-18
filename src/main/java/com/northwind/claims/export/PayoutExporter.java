package com.northwind.claims.export;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.Policy;
import com.northwind.claims.money.Money;
import com.northwind.claims.payouts.PayoutCalculator;
import java.time.LocalDate;

/** Builds the payout CSV that finance imports into the payments system. */
public final class PayoutExporter {
    private PayoutExporter() {}

    public static String toCsv(Claim claim, Policy policy, LocalDate firstPayment) {
        StringBuilder csv = new StringBuilder("claim_id,payee,installment,due_date,amount\n");
        for (PayoutCalculator.Installment installment : PayoutCalculator.schedule(claim, policy, firstPayment)) {
            csv.append(text(claim.getId())).append(',')
               .append(text(claim.getPayeeName())).append(',')
               .append(installment.number()).append(',')
               .append(installment.dueDate()).append(',')
               .append(Money.format(installment.amountCents())).append('\n');
        }
        return csv.toString();
    }

    /** A free-text cell: neutralized against spreadsheet formulas, then quoted per RFC 4180 when needed. */
    static String text(String value) {
        String cell = value == null ? "" : value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            cell = '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }
}
