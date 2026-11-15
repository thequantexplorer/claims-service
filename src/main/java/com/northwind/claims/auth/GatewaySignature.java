package com.northwind.claims.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signature the API gateway attaches to the identity headers it forwards.
 * The signature covers the user id, role, timestamp, HTTP method and request target,
 * so a captured signature cannot be reused for another user, role or request.
 */
public final class GatewaySignature {
    private GatewaySignature() {}

    public static String sign(String secret, String userId, String role, String timestamp, String method, String target) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = String.join("\n", userId, role, timestamp, method, target);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public static boolean matches(String secret, String signature, String userId, String role, String timestamp,
            String method, String target) {
        byte[] expected = sign(secret, userId, role, timestamp, method, target).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
