package com.northwind.claims.payout;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.Policy;
import com.northwind.claims.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** All payout math: the payable amount for a claim and its monthly installment schedule. */
public final class PayoutCalculator {
    public record Installment(int number, LocalDate dueDate, long amountCents) {}

    private PayoutCalculator() {}

    /** Amount the insurer pays: the claim minus the deductible, capped at the coverage limit. */
    public static long payableCents(Claim claim, Policy policy) {
        long afterDeductible = claim.getAmountCents() - policy.getDeductibleCents();
        if (afterDeductible <= 0) {
            return 0;
        }
        return Math.min(afterDeductible, policy.getCoverageLimitCents());
    }

    /** Monthly installments starting on the first payment date. */
    public static List<Installment> schedule(long payableCents, int installments, LocalDate firstPayment) {
        long[] amounts = Money.splitEvenly(payableCents, installments);
        List<Installment> out = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            out.add(new Installment(i + 1, firstPayment.plusMonths(i), amounts[i]));
        }
        return out;
    }

    /** Monthly installments of the claim's payable amount, using the claim's installment count. */
    public static List<Installment> schedule(Claim claim, Policy policy, LocalDate firstPayment) {
        return schedule(payableCents(claim, policy), claim.getInstallments(), firstPayment);
    }
}
