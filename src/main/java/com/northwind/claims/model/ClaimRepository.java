package com.northwind.claims.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, String> {
    List<Claim> findByCustomerId(String customerId);
}
