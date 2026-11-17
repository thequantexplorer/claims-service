package com.northwind.claims.model;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ClaimRepository extends JpaRepository<Claim, String> {
    List<Claim> findByCustomerId(String customerId);

    /** Row-locks the claim (SELECT ... FOR UPDATE) until the current transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Claim> findWithLockById(String id);
}
