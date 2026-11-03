package com.northwind.claims.web;

import com.northwind.claims.auth.CurrentUser;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Loads a claim the caller is allowed to see. Customers only see their own claims; others get a 404. */
public final class ClaimAccess {
    private ClaimAccess() {}

    public static Claim findVisible(ClaimRepository claims, String id, CurrentUser user) {
        Claim claim = claims.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!user.canSeeAllClaims() && !claim.getCustomerId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return claim;
    }
}
