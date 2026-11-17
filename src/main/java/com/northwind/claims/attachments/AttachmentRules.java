package com.northwind.claims.attachments;

import com.northwind.claims.model.ClaimStatus;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Pure validation rules for claim attachments. */
public final class AttachmentRules {
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_FILES_PER_CLAIM = 20;
    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final String FALLBACK_FILE_NAME = "attachment";

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String PDF = "application/pdf";

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private AttachmentRules() {}

    public static boolean acceptsUploads(ClaimStatus status) {
        return status == ClaimStatus.SUBMITTED || status == ClaimStatus.IN_REVIEW;
    }

    /** Detects the content type from magic bytes only; the client's Content-Type and file extension are ignored. */
    public static Optional<String> detectContentType(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) return Optional.of(JPEG);
        if (startsWith(bytes, PNG_MAGIC)) return Optional.of(PNG);
        if (startsWith(bytes, PDF_MAGIC)) return Optional.of(PDF);
        return Optional.empty();
    }

    /** Strips path components and control characters, and truncates to 255 characters. */
    public static String sanitizeFileName(String original) {
        if (original == null) return FALLBACK_FILE_NAME;
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        StringBuilder clean = new StringBuilder();
        name.codePoints()
                .filter(cp -> !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT
                        && cp != 0x2028 && cp != 0x2029)
                .limit(MAX_FILE_NAME_LENGTH)
                .forEach(clean::appendCodePoint);
        String result = clean.toString().strip();
        if (result.isEmpty() || result.equals(".") || result.equals("..")) return FALLBACK_FILE_NAME;
        return result;
    }

    /** {@code attachment; filename*=UTF-8''<percent-encoded name>} per RFC 6266 / RFC 5987. */
    public static String contentDisposition(String fileName) {
        StringBuilder value = new StringBuilder("attachment; filename*=UTF-8''");
        for (byte b : fileName.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (isAttrChar(c)) {
                value.append((char) c);
            } else {
                value.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return value.toString();
    }

    private static boolean isAttrChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "!#$&+-.^_`|~".indexOf(c) >= 0;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes != null && bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
