package com.northwind.claims.attachments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northwind.claims.model.ClaimStatus;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttachmentRulesTest {
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void onlySubmittedAndInReviewClaimsAcceptUploads() {
        assertTrue(AttachmentRules.acceptsUploads(ClaimStatus.SUBMITTED));
        assertTrue(AttachmentRules.acceptsUploads(ClaimStatus.IN_REVIEW));
        assertFalse(AttachmentRules.acceptsUploads(ClaimStatus.APPROVED));
        assertFalse(AttachmentRules.acceptsUploads(ClaimStatus.DENIED));
        assertFalse(AttachmentRules.acceptsUploads(ClaimStatus.PAID));
    }

    @Test
    void detectsTypesFromMagicBytes() {
        assertEquals(Optional.of("image/jpeg"), AttachmentRules.detectContentType(JPEG));
        assertEquals(Optional.of("image/png"), AttachmentRules.detectContentType(PNG));
        assertEquals(Optional.of("application/pdf"), AttachmentRules.detectContentType(PDF));
    }

    @Test
    void rejectsUnknownTruncatedAndEmptyContent() {
        assertEquals(Optional.empty(), AttachmentRules.detectContentType("<svg onload=alert(1)>".getBytes()));
        assertEquals(Optional.empty(), AttachmentRules.detectContentType("GIF89a".getBytes()));
        assertEquals(Optional.empty(), AttachmentRules.detectContentType(new byte[] {(byte) 0xFF, (byte) 0xD8}));
        assertEquals(Optional.empty(), AttachmentRules.detectContentType("%PDF".getBytes()));
        assertEquals(Optional.empty(), AttachmentRules.detectContentType(new byte[0]));
        assertEquals(Optional.empty(), AttachmentRules.detectContentType(null));
    }

    @Test
    void stripsPathComponents() {
        assertEquals("passwd", AttachmentRules.sanitizeFileName("../../etc/passwd"));
        assertEquals("evil.pdf", AttachmentRules.sanitizeFileName("C:\\Users\\ana\\..\\evil.pdf"));
        assertEquals("receipt.pdf", AttachmentRules.sanitizeFileName("receipt.pdf"));
    }

    @Test
    void stripsControlCharacters() {
        assertEquals("photo.jpgSet-Cookie: x", AttachmentRules.sanitizeFileName("photo.jpg\r\nSet-Cookie: x"));
        assertEquals("ab.pdf", AttachmentRules.sanitizeFileName("a\u0000b\u202E.pdf"));
    }

    @Test
    void truncatesTo255Characters() {
        String name = AttachmentRules.sanitizeFileName("a".repeat(300) + ".pdf");
        assertEquals(255, name.length());
        String emoji = AttachmentRules.sanitizeFileName("\uD83D\uDCF7".repeat(300));
        assertEquals(255, emoji.codePointCount(0, emoji.length()));
    }

    @Test
    void fallsBackWhenNothingIsLeft() {
        assertEquals("attachment", AttachmentRules.sanitizeFileName(null));
        assertEquals("attachment", AttachmentRules.sanitizeFileName(""));
        assertEquals("attachment", AttachmentRules.sanitizeFileName("uploads/"));
        assertEquals("attachment", AttachmentRules.sanitizeFileName(".."));
        assertEquals("attachment", AttachmentRules.sanitizeFileName("\r\n"));
    }

    @Test
    void contentDispositionPercentEncodesTheName() {
        assertEquals("attachment; filename*=UTF-8''receipt.pdf", AttachmentRules.contentDisposition("receipt.pdf"));
        assertEquals("attachment; filename*=UTF-8''my%20photo%20%221%22%3B.jpg",
                AttachmentRules.contentDisposition("my photo \"1\";.jpg"));
        assertEquals("attachment; filename*=UTF-8''fa%C3%A7ade.png", AttachmentRules.contentDisposition("façade.png"));
    }
}
