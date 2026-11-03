package com.northwind.claims.web;

import com.northwind.claims.auth.CurrentUser;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.model.PolicyRepository;
import com.northwind.claims.payout.PayoutCalculator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimRepository claims;
    private final PolicyRepository policies;

    public ClaimController(ClaimRepository claims, PolicyRepository policies) {
        this.claims = claims;
        this.policies = policies;
    }

    @GetMapping
    public List<Claim> list(@RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        return user.canSeeAllClaims() ? claims.findAll() : claims.findByCustomerId(user.id());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id, @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        Claim claim = ClaimAccess.findVisible(claims, id, user);
        var policy = policies.findById(claim.getPolicyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of("claim", claim, "payableCents", PayoutCalculator.payableCents(claim, policy));
    }
}
