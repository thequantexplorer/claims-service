package com.northwind.claims.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClaimIntakeValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final String AMOUNT_ERROR = "amount must be positive";
    private static final String INSTALLMENTS_ERROR = "installments must be between 1 and 12";
    private static final String FUTURE_ERROR = "incident date is in the future";
    private static final String WINDOW_ERROR = "reported more than 60 days after the incident";
    private static final String PAYEE_ERROR = "payee name is required";

    private static Claim claim(long amountCents, int installments, LocalDate incident, LocalDate reported, String payee,
                               ClaimType type, ClaimStatus status) {
        return new Claim("C", "P", "cust", payee, type, status, amountCents, installments, incident, reported);
    }

    private static Claim claim(long amountCents, int installments, LocalDate incident, LocalDate reported, String payee) {
        return claim(amountCents, installments, incident, reported, payee, ClaimType.AUTO, ClaimStatus.SUBMITTED);
    }

    private static Claim valid() {
        return claim(100_000, 1, TODAY.minusDays(5), TODAY.minusDays(3), "Ana Ruiz");
    }

    private static Claim withAmount(long cents) {
        return claim(cents, 1, TODAY.minusDays(5), TODAY.minusDays(3), "Ana Ruiz");
    }

    private static Claim withInstallments(int n) {
        return claim(100_000, n, TODAY.minusDays(5), TODAY.minusDays(3), "Ana Ruiz");
    }

    private static Claim withReportingDelay(long days) {
        LocalDate incident = TODAY.minusDays(100);
        return claim(100_000, 1, incident, incident.plusDays(days), "Ana Ruiz");
    }

    private static Claim withPayee(String payee) {
        return claim(100_000, 1, TODAY.minusDays(5), TODAY.minusDays(3), payee);
    }

    @Test
    void constantsMatchDocumentedRules() {
        assertEquals(60, ClaimIntakeValidator.REPORTING_WINDOW_DAYS);
        assertEquals(12, ClaimIntakeValidator.MAX_INSTALLMENTS);
    }

    @Test
    void validClaimHasNoErrors() {
        assertEquals(List.of(), ClaimIntakeValidator.validate(valid(), TODAY));
    }

    @Test
    void zeroAmountIsRejected() {
        assertEquals(List.of(AMOUNT_ERROR), ClaimIntakeValidator.validate(withAmount(0), TODAY));
    }

    @Test
    void negativeAmountIsRejected() {
        assertEquals(List.of(AMOUNT_ERROR), ClaimIntakeValidator.validate(withAmount(-1), TODAY));
        assertEquals(List.of(AMOUNT_ERROR), ClaimIntakeValidator.validate(withAmount(Long.MIN_VALUE), TODAY));
    }

    @Test
    void oneCentIsTheSmallestValidAmount() {
        assertEquals(List.of(), ClaimIntakeValidator.validate(withAmount(1), TODAY));
    }

    @Test
    void veryLargeAmountIsAccepted() {
        assertEquals(List.of(), ClaimIntakeValidator.validate(withAmount(Long.MAX_VALUE), TODAY));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 6, 11, 12})
    void installmentsWithinOneToTwelveAreAccepted(int installments) {
        assertEquals(List.of(), ClaimIntakeValidator.validate(withInstallments(installments), TODAY));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 13, 24, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void installmentsOutsideOneToTwelveAreRejected(int installments) {
        assertEquals(List.of(INSTALLMENTS_ERROR), ClaimIntakeValidator.validate(withInstallments(installments), TODAY));
    }

    @Test
    void incidentTodayIsAccepted() {
        assertEquals(List.of(), ClaimIntakeValidator.validate(claim(100_000, 1, TODAY, TODAY, "Ana Ruiz"), TODAY));
    }

    @Test
    void incidentOneDayInTheFutureIsRejected() {
        Claim c = claim(100_000, 1, TODAY.plusDays(1), TODAY.plusDays(1), "Ana Ruiz");
        assertEquals(List.of(FUTURE_ERROR), ClaimIntakeValidator.validate(c, TODAY));
    }

    @Test
    void futureIncidentSkipsReportingWindowCheck() {
        Claim c = claim(100_000, 1, TODAY.plusDays(1), TODAY.plusDays(200), "Ana Ruiz");
        assertEquals(List.of(FUTURE_ERROR), ClaimIntakeValidator.validate(c, TODAY));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 59, 60})
    void reportingWithinSixtyDaysIsAccepted(long days) {
        assertEquals(List.of(), ClaimIntakeValidator.validate(withReportingDelay(days), TODAY));
    }

    @ParameterizedTest
    @ValueSource(longs = {61, 62, 100})
    void reportingMoreThanSixtyDaysAfterIncidentIsRejected(long days) {
        assertEquals(List.of(WINDOW_ERROR), ClaimIntakeValidator.validate(withReportingDelay(days), TODAY));
    }

    @Test
    void reportingWindowIsMeasuredToReportedDateNotToday() {
        Claim c = claim(100_000, 1, TODAY.minusDays(365), TODAY.minusDays(364), "Ana Ruiz");
        assertEquals(List.of(), ClaimIntakeValidator.validate(c, TODAY));
    }

    @Test
    void reportingWindowBoundaryHoldsAcrossLeapDay() {
        LocalDate incident = LocalDate.of(2024, 1, 31);
        assertEquals(List.of(), ClaimIntakeValidator.validate(
                claim(100_000, 1, incident, LocalDate.of(2024, 3, 31), "Ana Ruiz"), TODAY));
        assertEquals(List.of(WINDOW_ERROR), ClaimIntakeValidator.validate(
                claim(100_000, 1, incident, LocalDate.of(2024, 4, 1), "Ana Ruiz"), TODAY));
    }

    @Test
    void nullPayeeIsRejected() {
        assertEquals(List.of(PAYEE_ERROR), ClaimIntakeValidator.validate(withPayee(null), TODAY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void emptyOrBlankPayeeIsRejected(String payee) {
        assertEquals(List.of(PAYEE_ERROR), ClaimIntakeValidator.validate(withPayee(payee), TODAY));
    }

    @Test
    void singleCharacterPayeeIsAccepted() {
        assertEquals(List.of(), ClaimIntakeValidator.validate(withPayee("A"), TODAY));
    }

    @Test
    void allErrorsAreReportedInRuleOrder() {
        Claim c = claim(0, 0, TODAY.plusDays(1), TODAY.plusDays(1), " ");
        assertEquals(List.of(AMOUNT_ERROR, INSTALLMENTS_ERROR, FUTURE_ERROR, PAYEE_ERROR),
                ClaimIntakeValidator.validate(c, TODAY));
    }

    @Test
    void reportingWindowErrorCombinesWithOtherErrors() {
        LocalDate incident = TODAY.minusDays(100);
        Claim c = claim(-500, 13, incident, incident.plusDays(61), null);
        assertEquals(List.of(AMOUNT_ERROR, INSTALLMENTS_ERROR, WINDOW_ERROR, PAYEE_ERROR),
                ClaimIntakeValidator.validate(c, TODAY));
    }

    @ParameterizedTest
    @EnumSource(ClaimType.class)
    void everyClaimTypeIsValidatedTheSame(ClaimType type) {
        Claim ok = claim(100_000, 1, TODAY.minusDays(5), TODAY.minusDays(3), "Ana Ruiz", type, ClaimStatus.SUBMITTED);
        Claim bad = claim(0, 1, TODAY.minusDays(5), TODAY.minusDays(3), "Ana Ruiz", type, ClaimStatus.SUBMITTED);
        assertEquals(List.of(), ClaimIntakeValidator.validate(ok, TODAY));
        assertEquals(List.of(AMOUNT_ERROR), ClaimIntakeValidator.validate(bad, TODAY));
    }

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    void everyClaimStatusIsValidatedTheSame(ClaimStatus status) {
        Claim ok = claim(100_000, 1, TODAY.minusDays(5), TODAY.minusDays(3), "Ana Ruiz", ClaimType.THEFT, status);
        Claim bad = claim(100_000, 13, TODAY.minusDays(5), TODAY.minusDays(3), "Ana Ruiz", ClaimType.THEFT, status);
        assertEquals(List.of(), ClaimIntakeValidator.validate(ok, TODAY));
        assertEquals(List.of(INSTALLMENTS_ERROR), ClaimIntakeValidator.validate(bad, TODAY));
    }

    @Test
    void returnedListIsFreshPerCall() {
        List<String> first = ClaimIntakeValidator.validate(withAmount(0), TODAY);
        first.add("mutated");
        assertEquals(List.of(AMOUNT_ERROR), ClaimIntakeValidator.validate(withAmount(0), TODAY));
        assertTrue(first.contains("mutated"));
    }
}
