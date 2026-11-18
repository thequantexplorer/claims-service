package com.northwind.claims.export;

import com.northwind.claims.auth.CurrentUser;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.Policy;
import com.northwind.claims.model.PolicyRepository;
import com.northwind.claims.web.ClaimAccess;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PayoutExportController {
    private final ClaimRepository claims;
    private final PolicyRepository policies;

    public PayoutExportController(ClaimRepository claims, PolicyRepository policies) {
        this.claims = claims;
        this.policies = policies;
    }

    /** Payout schedule as CSV for the finance team's payments import. */
    @GetMapping("/api/claims/{id}/payouts.csv")
    public ResponseEntity<String> export(@PathVariable String id, @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        Claim claim = ClaimAccess.findVisible(claims, id, user);
        Policy policy = policies.findById(claim.getPolicyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String csv = PayoutExporter.toCsv(claim, policy, LocalDate.now().plusDays(7));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + claim.getId() + "-payouts.csv\"")
                .body(csv);
    }
}
