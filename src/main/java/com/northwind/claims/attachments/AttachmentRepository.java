package com.northwind.claims.attachments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    /** Metadata-only projection so listing never loads file bytes. */
    @Query("""
            select new com.northwind.claims.attachments.AttachmentDto(
                a.id, a.claimId, a.fileName, a.contentType, a.sizeBytes, a.uploadedBy, a.uploadedAt)
            from Attachment a where a.claimId = :claimId order by a.uploadedAt asc""")
    List<AttachmentDto> findByClaimIdOrderByUploadedAtAsc(String claimId);

    long countByClaimId(String claimId);

    Optional<Attachment> findByIdAndClaimId(UUID id, String claimId);
}
