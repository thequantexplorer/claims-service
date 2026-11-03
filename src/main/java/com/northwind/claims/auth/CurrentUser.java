package com.northwind.claims.auth;

/** The authenticated caller. Set by {@link AuthFilter} from the gateway's identity headers. */
public record CurrentUser(String id, Role role) {
    public static final String ATTRIBUTE = "currentUser";

    public boolean canSeeAllClaims() {
        return role == Role.ADJUSTER || role == Role.FINANCE;
    }
}
