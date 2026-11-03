package com.northwind.claims.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDate;

@Entity
public class Claim {
    @Id
    private String id;
    private String policyId;
    private String customerId;
    private String payeeName;
    @Enumerated(EnumType.STRING)
    private ClaimType type;
    @Enumerated(EnumType.STRING)
    private ClaimStatus status;
    private long amountCents;
    private int installments;
    private LocalDate incidentDate;
    private LocalDate reportedDate;

    protected Claim() {}

    public Claim(String id, String policyId, String customerId, String payeeName, ClaimType type,
                 ClaimStatus status, long amountCents, int installments, LocalDate incidentDate, LocalDate reportedDate) {
        this.id = id;
        this.policyId = policyId;
        this.customerId = customerId;
        this.payeeName = payeeName;
        this.type = type;
        this.status = status;
        this.amountCents = amountCents;
        this.installments = installments;
        this.incidentDate = incidentDate;
        this.reportedDate = reportedDate;
    }

    public String getId() { return id; }
    public String getPolicyId() { return policyId; }
    public String getCustomerId() { return customerId; }
    public String getPayeeName() { return payeeName; }
    public ClaimType getType() { return type; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public long getAmountCents() { return amountCents; }
    public int getInstallments() { return installments; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public LocalDate getReportedDate() { return reportedDate; }
}
