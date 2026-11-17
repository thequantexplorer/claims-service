package com.northwind.claims.attachments;

import java.time.Instant;
import java.util.UUID;

/** Attachment metadata. Never carries the file bytes. */
public record AttachmentDto(UUID id, String claimId, String fileName, String contentType, long sizeBytes,
                            String uploadedBy, Instant uploadedAt) {}
