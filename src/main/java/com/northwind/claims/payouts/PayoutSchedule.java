package com.northwind.claims.payouts;

import com.northwind.claims.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PayoutSchedule {
    public record Installment(int number, LocalDate dueDate, long amountCents) {}

    private PayoutSchedule() {}

    /** Monthly installments starting on the first payment date. */
    public static List<Installment> build(long payableCents, int installments, LocalDate firstPayment) {
        long[] amounts = Money.splitEvenly(payableCents, installments);
        List<Installment> out = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            out.add(new Installment(i + 1, firstPayment.plusMonths(i), amounts[i]));
        }
        return out;
    }
}
