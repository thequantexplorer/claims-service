package com.northwind.claims.notifications;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.money.Money;

public final class NotificationTemplates {
    private NotificationTemplates() {}

    public static String statusEmail(Claim claim, long payableCents) {
        return switch (claim.getStatus()) {
            case SUBMITTED -> "We received claim " + claim.getId() + ".";
            case IN_REVIEW -> "An adjuster is reviewing claim " + claim.getId() + ".";
            case APPROVED -> "Claim " + claim.getId() + " is approved. We will pay $" + Money.format(payableCents)
                    + " in " + claim.getInstallments() + " installment(s).";
            case DENIED -> "Claim " + claim.getId() + " was not approved. Your adjuster will contact you.";
            case PAID -> "Claim " + claim.getId() + " is paid in full.";
        };
    }

    public static boolean isFinal(ClaimStatus status) {
        return status == ClaimStatus.DENIED || status == ClaimStatus.PAID;
    }
}
