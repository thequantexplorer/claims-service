package com.northwind.claims.attachments;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimAttachmentApiTest {
    static final String OPEN_CLAIM = "CLM-1002";
    static final String CLOSED_CLAIM = "CLM-1001";
    static final String BENS_CLAIM = "CLM-2001";

    @Autowired
    MockMvc mvc;
    @Autowired
    AttachmentRepository attachments;

    static MockMultipartFile file(String name, String clientType, byte[] bytes) {
        return new MockMultipartFile("file", name, clientType, bytes);
    }

    static <T extends AbstractMockHttpServletRequestBuilder<?>> T as(T request, String userId, String role) {
        request.header("X-User-Id", userId).header("X-User-Role", role);
        return request;
    }

    MockMultipartHttpServletRequestBuilder upload(String claimId, MockMultipartFile file, String userId, String role) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/claims/{id}/attachments", claimId);
        if (file != null) request.file(file);
        return as(request, userId, role);
    }

    MockMultipartHttpServletRequestBuilder anaUploads(String claimId, MockMultipartFile file) {
        return upload(claimId, file, "cust-ana", "CUSTOMER");
    }

    String uploadAsAna(MockMultipartFile file) throws Exception {
        String body = mvc.perform(anaUploads(OPEN_CLAIM, file)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // AC1: the owning customer can upload a JPEG, PNG or PDF to an open claim.
    @Test
    void customerUploadsToOwnOpenClaim() throws Exception {
        String body = mvc.perform(anaUploads(OPEN_CLAIM, file("dent.jpg", "image/jpeg", AttachmentRulesTest.JPEG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimId").value(OPEN_CLAIM))
                .andExpect(jsonPath("$.fileName").value("dent.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(AttachmentRulesTest.JPEG.length))
                .andExpect(jsonPath("$.uploadedBy").value("cust-ana"))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty())
                .andExpect(jsonPath("$", not(hasKey("data"))))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(anaUploads(OPEN_CLAIM, file("dent.jpg", "image/jpeg", AttachmentRulesTest.JPEG)))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "/api/claims/" + OPEN_CLAIM + "/attachments/")));
        assertEquals(2, attachments.countByClaimId(OPEN_CLAIM));
        mvc.perform(as(get("/api/claims/{c}/attachments/{a}", OPEN_CLAIM, id), "cust-ana", "CUSTOMER"))
                .andExpect(status().isOk());
    }

    // AC2: type comes from magic bytes; the client's Content-Type and extension are ignored.
    @Test
    void storedTypeComesFromMagicBytesNotTheClient() throws Exception {
        mvc.perform(anaUploads(OPEN_CLAIM, file("receipt.jpg", "image/jpeg", AttachmentRulesTest.PDF)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
        mvc.perform(anaUploads(OPEN_CLAIM, file("scan.pdf", "application/octet-stream", AttachmentRulesTest.PNG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void spoofedTypeIsRejectedWith415() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        mvc.perform(anaUploads(OPEN_CLAIM, file("photo.png", "image/png", html)))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(anaUploads(OPEN_CLAIM, file("doc.pdf", "application/pdf", "GIF89a....".getBytes())))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0, attachments.countByClaimId(OPEN_CLAIM));
    }

    // AC3: missing or empty files are rejected.
    @Test
    void missingOrEmptyFileIsRejectedWith400() throws Exception {
        mvc.perform(anaUploads(OPEN_CLAIM, null)).andExpect(status().isBadRequest());
        mvc.perform(anaUploads(OPEN_CLAIM, file("empty.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest());
        assertEquals(0, attachments.countByClaimId(OPEN_CLAIM));
    }

    // AC4: 10 MB per file, 20 files per claim.
    @Test
    void fileOverTenMegabytesIsRejectedWith413() throws Exception {
        byte[] big = new byte[(int) AttachmentRules.MAX_FILE_BYTES + 1];
        System.arraycopy(AttachmentRulesTest.PDF, 0, big, 0, AttachmentRulesTest.PDF.length);
        mvc.perform(anaUploads(OPEN_CLAIM, file("big.pdf", "application/pdf", big)))
                .andExpect(status().isPayloadTooLarge());
        assertEquals(0, attachments.countByClaimId(OPEN_CLAIM));
    }

    @Test
    void fileOfExactlyTenMegabytesIsAccepted() throws Exception {
        byte[] max = new byte[(int) AttachmentRules.MAX_FILE_BYTES];
        System.arraycopy(AttachmentRulesTest.PDF, 0, max, 0, AttachmentRulesTest.PDF.length);
        mvc.perform(anaUploads(OPEN_CLAIM, file("max.pdf", "application/pdf", max)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(AttachmentRules.MAX_FILE_BYTES));
    }

    @Test
    void twentyFirstFileIsRejectedWith413() throws Exception {
        for (int i = 0; i < AttachmentRules.MAX_FILES_PER_CLAIM; i++) {
            attachments.save(new Attachment(OPEN_CLAIM, "f" + i + ".pdf", "application/pdf", "cust-ana",
                    Instant.now(), AttachmentRulesTest.PDF));
        }
        mvc.perform(anaUploads(OPEN_CLAIM, file("one-more.pdf", "application/pdf", AttachmentRulesTest.PDF)))
                .andExpect(status().isPayloadTooLarge());
        assertEquals(AttachmentRules.MAX_FILES_PER_CLAIM, attachments.countByClaimId(OPEN_CLAIM));
    }

    // AC5: closed claims do not accept uploads.
    @Test
    void uploadToClosedClaimIsRejectedWith409() throws Exception {
        mvc.perform(anaUploads(CLOSED_CLAIM, file("late.pdf", "application/pdf", AttachmentRulesTest.PDF)))
                .andExpect(status().isConflict());
        assertEquals(0, attachments.countByClaimId(CLOSED_CLAIM));
    }

    // AC6: only the owning customer can upload; other customers cannot learn the claim exists.
    @Test
    void otherCustomersGet404OnUploadListAndDownload() throws Exception {
        String id = uploadAsAna(file("dent.jpg", "image/jpeg", AttachmentRulesTest.JPEG));
        mvc.perform(upload(OPEN_CLAIM, file("x.pdf", "application/pdf", AttachmentRulesTest.PDF), "cust-ben", "CUSTOMER"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{c}/attachments", OPEN_CLAIM), "cust-ben", "CUSTOMER"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{c}/attachments/{a}", OPEN_CLAIM, id), "cust-ben", "CUSTOMER"))
                .andExpect(status().isNotFound());
        mvc.perform(anaUploads(BENS_CLAIM, file("x.pdf", "application/pdf", AttachmentRulesTest.PDF)))
                .andExpect(status().isNotFound());
        mvc.perform(anaUploads("CLM-9999", file("x.pdf", "application/pdf", AttachmentRulesTest.PDF)))
                .andExpect(status().isNotFound());
        assertEquals(1, attachments.countByClaimId(OPEN_CLAIM));
    }

    @Test
    void invisibilityWinsOverEveryOtherUploadError() throws Exception {
        // Ben's claim is closed and the file is invalid, but Ana must still only see 404.
        mvc.perform(anaUploads(BENS_CLAIM, null)).andExpect(status().isNotFound());
    }

    @Test
    void staffCannotUploadButCanListAndDownload() throws Exception {
        String id = uploadAsAna(file("dent.jpg", "image/jpeg", AttachmentRulesTest.JPEG));
        for (String role : new String[] {"ADJUSTER", "FINANCE"}) {
            mvc.perform(upload(OPEN_CLAIM, file("x.pdf", "application/pdf", AttachmentRulesTest.PDF), "staff-1", role))
                    .andExpect(status().isForbidden());
            mvc.perform(as(get("/api/claims/{c}/attachments", OPEN_CLAIM), "staff-1", role))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(as(get("/api/claims/{c}/attachments/{a}", OPEN_CLAIM, id), "staff-1", role))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(AttachmentRulesTest.JPEG));
        }
        assertEquals(1, attachments.countByClaimId(OPEN_CLAIM));
    }

    @Test
    void requestsWithoutIdentityAreRejected() throws Exception {
        mvc.perform(multipart("/api/claims/{id}/attachments", OPEN_CLAIM)
                        .file(file("x.pdf", "application/pdf", AttachmentRulesTest.PDF)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/{id}/attachments", OPEN_CLAIM)).andExpect(status().isUnauthorized());
    }

    // AC7: list returns metadata only, oldest first.
    @Test
    void listReturnsMetadataOldestFirstWithoutBytes() throws Exception {
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        attachments.save(new Attachment(OPEN_CLAIM, "second.pdf", "application/pdf", "cust-ana", t.plusSeconds(60),
                AttachmentRulesTest.PDF));
        attachments.save(new Attachment(OPEN_CLAIM, "first.png", "image/png", "cust-ana", t, AttachmentRulesTest.PNG));
        attachments.save(new Attachment(CLOSED_CLAIM, "other-claim.pdf", "application/pdf", "cust-ana", t,
                AttachmentRulesTest.PDF));
        mvc.perform(as(get("/api/claims/{c}/attachments", OPEN_CLAIM), "cust-ana", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fileName").value("first.png"))
                .andExpect(jsonPath("$[0].sizeBytes").value(AttachmentRulesTest.PNG.length))
                .andExpect(jsonPath("$[1].fileName").value("second.pdf"))
                .andExpect(jsonPath("$[0]", not(hasKey("data"))));
        mvc.perform(as(get("/api/claims/{c}/attachments", CLOSED_CLAIM), "cust-ana", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // AC8: download returns stored bytes with safe headers.
    @Test
    void downloadReturnsBytesWithSafeHeaders() throws Exception {
        String id = uploadAsAna(file("../../etc/receipt\r\n.pdf", "text/html", AttachmentRulesTest.PDF));
        mvc.perform(as(get("/api/claims/{c}/attachments/{a}", OPEN_CLAIM, id), "cust-ana", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(AttachmentRulesTest.PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename*=UTF-8''receipt.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void attachmentFromAnotherClaimIs404() throws Exception {
        String id = uploadAsAna(file("dent.jpg", "image/jpeg", AttachmentRulesTest.JPEG));
        mvc.perform(as(get("/api/claims/{c}/attachments/{a}", CLOSED_CLAIM, id), "cust-ana", "CUSTOMER"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{c}/attachments/{a}", BENS_CLAIM, id), "staff-1", "ADJUSTER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownOrMalformedAttachmentIdIs404() throws Exception {
        mvc.perform(as(get("/api/claims/{c}/attachments/{a}", OPEN_CLAIM, java.util.UUID.randomUUID()),
                        "cust-ana", "CUSTOMER"))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{c}/attachments/{a}", OPEN_CLAIM, "not-a-uuid"), "cust-ana", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }
}
