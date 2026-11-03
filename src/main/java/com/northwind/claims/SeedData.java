package com.northwind.claims;

import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.ClaimStatus;
import com.northwind.claims.model.ClaimType;
import com.northwind.claims.model.Policy;
import com.northwind.claims.model.PolicyRepository;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SeedData implements CommandLineRunner {
    private final ClaimRepository claims;
    private final PolicyRepository policies;

    public SeedData(ClaimRepository claims, PolicyRepository policies) {
        this.claims = claims;
        this.policies = policies;
    }

    @Override
    public void run(String... args) {
        policies.save(new Policy("POL-1001", "cust-ana", 50_000, 2_500_000));
        policies.save(new Policy("POL-2002", "cust-ben", 100_000, 5_000_000));
        LocalDate d = LocalDate.of(2026, 10, 1);
        claims.save(new Claim("CLM-1001", "POL-1001", "cust-ana", "Ana Ruiz", ClaimType.WATER_DAMAGE,
                ClaimStatus.APPROVED, 1_050_000, 3, d, d.plusDays(2)));
        claims.save(new Claim("CLM-1002", "POL-1001", "cust-ana", "Ana Ruiz", ClaimType.AUTO,
                ClaimStatus.IN_REVIEW, 320_000, 1, d.plusDays(10), d.plusDays(11)));
        claims.save(new Claim("CLM-2001", "POL-2002", "cust-ben", "Ben Okafor", ClaimType.THEFT,
                ClaimStatus.APPROVED, 2_100_000, 6, d.plusDays(5), d.plusDays(9)));
    }
}
