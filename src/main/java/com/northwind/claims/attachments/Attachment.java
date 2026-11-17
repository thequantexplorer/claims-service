package com.northwind.claims.attachments;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A file a customer attached to a claim. Append-only: never updated or deleted. */
@Entity
@Table(indexes = @Index(name = "idx_attachment_claim", columnList = "claimId, uploadedAt"))
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, updatable = false)
    private String claimId;
    @Column(nullable = false, updatable = false, length = AttachmentRules.MAX_FILE_NAME_LENGTH)
    private String fileName;
    @Column(nullable = false, updatable = false)
    private String contentType;
    @Column(nullable = false, updatable = false)
    private long sizeBytes;
    @Column(nullable = false, updatable = false)
    private String uploadedBy;
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, updatable = false)
    private byte[] data;

    protected Attachment() {}

    public Attachment(String claimId, String fileName, String contentType, String uploadedBy, Instant uploadedAt,
                      byte[] data) {
        this.claimId = claimId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = data.length;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.data = data;
    }

    public UUID getId() { return id; }
    public String getClaimId() { return claimId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public byte[] getData() { return data; }

    public AttachmentDto toDto() {
        return new AttachmentDto(id, claimId, fileName, contentType, sizeBytes, uploadedBy, uploadedAt);
    }
}
