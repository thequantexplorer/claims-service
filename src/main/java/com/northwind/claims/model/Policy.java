package com.northwind.claims.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Policy {
    @Id
    private String id;
    private String customerId;
    private long deductibleCents;
    private long coverageLimitCents;

    protected Policy() {}

    public Policy(String id, String customerId, long deductibleCents, long coverageLimitCents) {
        this.id = id;
        this.customerId = customerId;
        this.deductibleCents = deductibleCents;
        this.coverageLimitCents = coverageLimitCents;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public long getDeductibleCents() { return deductibleCents; }
    public long getCoverageLimitCents() { return coverageLimitCents; }
}
