package com.northwind.claims.attachments;

import com.northwind.claims.auth.CurrentUser;
import com.northwind.claims.auth.Role;
import com.northwind.claims.model.Claim;
import com.northwind.claims.model.ClaimRepository;
import com.northwind.claims.web.ClaimAccess;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/claims/{claimId}/attachments")
public class AttachmentController {
    private final ClaimRepository claims;
    private final AttachmentRepository attachments;

    public AttachmentController(ClaimRepository claims, AttachmentRepository attachments) {
        this.claims = claims;
        this.attachments = attachments;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<AttachmentDto> upload(@PathVariable String claimId,
                                                @RequestParam(value = "file", required = false) MultipartFile file,
                                                @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user)
            throws IOException {
        Claim claim = ClaimAccess.findVisible(claims, claimId, user);
        if (user.role() != Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the claim's customer can upload attachments");
        }
        if (!AttachmentRules.acceptsUploads(claim.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Claim is not open for attachments");
        }
        // Serialize uploads per claim so the count-then-insert below cannot race past the cap.
        claims.findWithLockById(claim.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (attachments.countByClaimId(claim.getId()) >= AttachmentRules.MAX_FILES_PER_CLAIM) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Claim already has the maximum number of attachments");
        }
        if (file != null && file.getSize() > AttachmentRules.MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds 10 MB");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty file is required");
        }
        byte[] bytes = file.getBytes();
        String contentType = AttachmentRules.detectContentType(bytes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Only JPEG, PNG and PDF files are accepted"));
        Attachment saved = attachments.save(new Attachment(claim.getId(),
                AttachmentRules.sanitizeFileName(file.getOriginalFilename()), contentType, user.id(), Instant.now(),
                bytes));
        return ResponseEntity.created(URI.create("/api/claims/" + claim.getId() + "/attachments/" + saved.getId()))
                .body(saved.toDto());
    }

    @GetMapping
    public List<AttachmentDto> list(@PathVariable String claimId,
                                    @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        Claim claim = ClaimAccess.findVisible(claims, claimId, user);
        return attachments.findByClaimIdOrderByUploadedAtAsc(claim.getId());
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable String claimId, @PathVariable String attachmentId,
                                           @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        Claim claim = ClaimAccess.findVisible(claims, claimId, user);
        Attachment attachment = parseId(attachmentId)
                .flatMap(id -> attachments.findByIdAndClaimId(id, claim.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, AttachmentRules.contentDisposition(attachment.getFileName()))
                .header("X-Content-Type-Options", "nosniff")
                .body(attachment.getData());
    }

    private static java.util.Optional<UUID> parseId(String id) {
        try {
            return java.util.Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
