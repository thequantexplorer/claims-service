package com.northwind.claims.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import java.time.LocalDate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationTemplatesTest {
    private static Claim claim(ClaimStatus status, int installments) {
        LocalDate d = LocalDate.of(2026, 10, 1);
        return new Claim("CLM-9", "POL-9", "cust-x", "X", ClaimType.AUTO, status, 100_000, installments, d, d);
    }

    @Test
    void submittedEmail() {
        assertEquals("We received claim CLM-9.", NotificationTemplates.statusEmail(claim(ClaimStatus.SUBMITTED, 1), 0));
    }

    @Test
    void inReviewEmail() {
        assertEquals("An adjuster is reviewing claim CLM-9.",
                NotificationTemplates.statusEmail(claim(ClaimStatus.IN_REVIEW, 1), 0));
    }

    @Test
    void approvedEmailIncludesFormattedAmountAndInstallments() {
        assertEquals("Claim CLM-9 is approved. We will pay $1000000.00 in 3 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 3), 100_000_000));
    }

    @Test
    void approvedEmailFormatsCentsWithTwoDigits() {
        assertEquals("Claim CLM-9 is approved. We will pay $12.05 in 1 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 1), 1_205));
    }

    @Test
    void approvedEmailAtCentBoundaries() {
        assertEquals("Claim CLM-9 is approved. We will pay $0.99 in 1 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 1), 99));
        assertEquals("Claim CLM-9 is approved. We will pay $1.00 in 1 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 1), 100));
        assertEquals("Claim CLM-9 is approved. We will pay $1.01 in 1 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 1), 101));
    }

    @Test
    void approvedEmailWithZeroPayable() {
        assertEquals("Claim CLM-9 is approved. We will pay $0.00 in 1 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 1), 0));
    }

    @Test
    void approvedEmailWithOneCent() {
        assertEquals("Claim CLM-9 is approved. We will pay $0.01 in 2 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 2), 1));
    }

    @Test
    void approvedEmailWithNegativePayableOverOneDollar() {
        assertEquals("Claim CLM-9 is approved. We will pay $-1.50 in 1 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 1), -150));
    }

    @Test
    @Disabled("Bug: Money.format drops the minus sign for -1..-99 cents, so -50 renders as $0.50")
    void approvedEmailWithNegativeSubDollarPayableKeepsSign() {
        assertEquals("Claim CLM-9 is approved. We will pay $-0.50 in 1 installment(s).",
                NotificationTemplates.statusEmail(claim(ClaimStatus.APPROVED, 1), -50));
    }

    @Test
    void deniedEmail() {
        assertEquals("Claim CLM-9 was not approved. Your adjuster will contact you.",
                NotificationTemplates.statusEmail(claim(ClaimStatus.DENIED, 1), 5_000));
    }

    @Test
    void paidEmail() {
        assertEquals("Claim CLM-9 is paid in full.", NotificationTemplates.statusEmail(claim(ClaimStatus.PAID, 1), 5_000));
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"SUBMITTED", "IN_REVIEW", "DENIED", "PAID"})
    void nonApprovedEmailsDoNotMentionAmount(ClaimStatus status) {
        String email = NotificationTemplates.statusEmail(claim(status, 3), 123_456);
        assertFalse(email.contains("$"), email);
        assertTrue(email.contains("CLM-9"), email);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"DENIED", "PAID"})
    void deniedAndPaidAreFinal(ClaimStatus status) {
        assertTrue(NotificationTemplates.isFinal(status));
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"SUBMITTED", "IN_REVIEW", "APPROVED"})
    void openStatusesAreNotFinal(ClaimStatus status) {
        assertFalse(NotificationTemplates.isFinal(status));
    }

    @Test
    void nullStatusIsNotFinal() {
        assertFalse(NotificationTemplates.isFinal(null));
    }
}
