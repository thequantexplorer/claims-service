package com.northwind.claims.intake;

import com.northwind.claims.model.Claim;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class ClaimIntakeValidator {
    public static final int REPORTING_WINDOW_DAYS = 60;
    public static final int MAX_INSTALLMENTS = 12;

    private ClaimIntakeValidator() {}

    public static List<String> validate(Claim claim, LocalDate today) {
        List<String> errors = new ArrayList<>();
        if (claim.getAmountCents() <= 0) {
            errors.add("amount must be positive");
        }
        if (claim.getInstallments() < 1 || claim.getInstallments() > MAX_INSTALLMENTS) {
            errors.add("installments must be between 1 and " + MAX_INSTALLMENTS);
        }
        if (claim.getIncidentDate().isAfter(today)) {
            errors.add("incident date is in the future");
        } else if (ChronoUnit.DAYS.between(claim.getIncidentDate(), claim.getReportedDate()) > REPORTING_WINDOW_DAYS) {
            errors.add("reported more than " + REPORTING_WINDOW_DAYS + " days after the incident");
        }
        if (claim.getPayeeName() == null || claim.getPayeeName().isBlank()) {
            errors.add("payee name is required");
        }
        return errors;
    }
}
