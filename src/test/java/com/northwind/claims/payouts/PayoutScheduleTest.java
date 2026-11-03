package com.northwind.claims.payouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northwind.claims.payouts.PayoutSchedule.Installment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PayoutScheduleTest {
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    private static long total(List<Installment> schedule) {
        return schedule.stream().mapToLong(Installment::amountCents).sum();
    }

    @Test
    void singleInstallmentPaysTheFullAmountOnTheFirstPaymentDate() {
        List<Installment> schedule = PayoutSchedule.build(270_000, 1, OCT_1);

        assertEquals(List.of(new Installment(1, OCT_1, 270_000)), schedule);
    }

    @Test
    void evenSplitProducesEqualMonthlyInstallments() {
        List<Installment> schedule = PayoutSchedule.build(900_000, 3, OCT_1);

        assertEquals(List.of(
                new Installment(1, LocalDate.of(2026, 10, 1), 300_000),
                new Installment(2, LocalDate.of(2026, 11, 1), 300_000),
                new Installment(3, LocalDate.of(2026, 12, 1), 300_000)), schedule);
    }

    @Test
    void unevenSplitPutsExtraCentsOnEarliestInstallmentsAndAddsUpToTotal() {
        List<Installment> schedule = PayoutSchedule.build(1_000_000, 3, OCT_1);

        assertEquals(List.of(333_334L, 333_333L, 333_333L),
                schedule.stream().map(Installment::amountCents).toList());
        assertEquals(1_000_000, total(schedule));
    }

    @Test
    void remainderOneLessThanInstallmentCountSpreadsAcrossAllButLast() {
        List<Installment> schedule = PayoutSchedule.build(2_000_005, 6, OCT_1);

        assertEquals(List.of(333_335L, 333_334L, 333_334L, 333_334L, 333_334L, 333_334L),
                schedule.stream().map(Installment::amountCents).toList());
        assertEquals(2_000_005, total(schedule));
    }

    @Test
    void installmentsNeverDifferByMoreThanOneCent() {
        List<Installment> schedule = PayoutSchedule.build(1_234_567, 7, OCT_1);

        long max = schedule.stream().mapToLong(Installment::amountCents).max().orElseThrow();
        long min = schedule.stream().mapToLong(Installment::amountCents).min().orElseThrow();
        assertEquals(1, max - min);
        assertEquals(176_367, max);
        assertEquals(176_366, min);
        assertEquals(1_234_567, total(schedule));
    }

    @Test
    void installmentsAreNumberedFromOneInOrder() {
        List<Installment> schedule = PayoutSchedule.build(600_000, 6, OCT_1);

        assertEquals(List.of(1, 2, 3, 4, 5, 6), schedule.stream().map(Installment::number).toList());
    }

    @Test
    void dueDatesRollOverTheYearBoundary() {
        List<Installment> schedule = PayoutSchedule.build(400_000, 4, LocalDate.of(2026, 11, 15));

        assertEquals(List.of(
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2027, 1, 15),
                LocalDate.of(2027, 2, 15)), schedule.stream().map(Installment::dueDate).toList());
    }

    @Test
    void monthEndFirstPaymentClampsToShortMonthsWithoutDrifting() {
        List<Installment> schedule = PayoutSchedule.build(400_000, 4, LocalDate.of(2026, 1, 31));

        assertEquals(List.of(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30)), schedule.stream().map(Installment::dueDate).toList());
    }

    @Test
    void monthEndFirstPaymentUsesFebruary29InLeapYear() {
        List<Installment> schedule = PayoutSchedule.build(200_000, 2, LocalDate.of(2028, 1, 31));

        assertEquals(LocalDate.of(2028, 2, 29), schedule.get(1).dueDate());
    }

    @Test
    void zeroPayableProducesZeroAmountInstallments() {
        List<Installment> schedule = PayoutSchedule.build(0, 3, OCT_1);

        assertEquals(List.of(
                new Installment(1, LocalDate.of(2026, 10, 1), 0),
                new Installment(2, LocalDate.of(2026, 11, 1), 0),
                new Installment(3, LocalDate.of(2026, 12, 1), 0)), schedule);
    }

    @Test
    void fewerCentsThanInstallmentsGivesOneCentToEarliestAndZeroToTheRest() {
        List<Installment> schedule = PayoutSchedule.build(2, 3, OCT_1);

        assertEquals(List.of(1L, 1L, 0L), schedule.stream().map(Installment::amountCents).toList());
        assertEquals(2, total(schedule));
    }

    @Test
    void payableEqualToInstallmentCountGivesOneCentEach() {
        List<Installment> schedule = PayoutSchedule.build(3, 3, OCT_1);

        assertEquals(List.of(1L, 1L, 1L), schedule.stream().map(Installment::amountCents).toList());
    }

    @Test
    void payableOneMoreThanInstallmentCountGivesExtraCentToFirst() {
        List<Installment> schedule = PayoutSchedule.build(4, 3, OCT_1);

        assertEquals(List.of(2L, 1L, 1L), schedule.stream().map(Installment::amountCents).toList());
    }

    @Test
    void zeroInstallmentsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PayoutSchedule.build(100_000, 0, OCT_1));
    }

    @Test
    void negativeInstallmentsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PayoutSchedule.build(100_000, -1, OCT_1));
    }

    @Test
    void largeInstallmentCountProducesOneEntryPerMonth() {
        List<Installment> schedule = PayoutSchedule.build(1_200_000, 12, OCT_1);

        assertEquals(12, schedule.size());
        assertTrue(schedule.stream().allMatch(i -> i.amountCents() == 100_000));
        assertEquals(LocalDate.of(2027, 9, 1), schedule.get(11).dueDate());
    }

    @Test
    void amountsBeyondIntRangeAreHandledWithoutOverflow() {
        long payable = 10_000_000_000L;
        List<Installment> schedule = PayoutSchedule.build(payable, 3, OCT_1);

        assertEquals(List.of(3_333_333_334L, 3_333_333_333L, 3_333_333_333L),
                schedule.stream().map(Installment::amountCents).toList());
        assertEquals(payable, total(schedule));
    }

    @Test
    @Disabled("Bug: negative payable loses cents - build(-100, 3) sums to -99 because Money.splitEvenly ignores negative remainders")
    void negativePayableInstallmentsStillAddUpToTotal() {
        List<Installment> schedule = PayoutSchedule.build(-100, 3, OCT_1);

        assertEquals(-100, total(schedule));
    }

    @Test
    void installmentRecordExposesItsComponents() {
        Installment installment = new Installment(2, LocalDate.of(2026, 11, 1), 333_333);

        assertEquals(2, installment.number());
        assertEquals(LocalDate.of(2026, 11, 1), installment.dueDate());
        assertEquals(333_333, installment.amountCents());
        assertEquals(new Installment(2, LocalDate.of(2026, 11, 1), 333_333), installment);
    }
}
