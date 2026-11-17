package com.northwind.claims.attachments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Not @Transactional: each upload must commit in its own transaction to exercise the race. */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentUploadCapTest {
    static final String OPEN_CLAIM = "CLM-1002";
    static final int THREADS = 5;

    @Autowired
    MockMvc mvc;
    @Autowired
    AttachmentRepository attachments;

    @BeforeEach
    @AfterEach
    void clean() {
        attachments.deleteAll(attachments.findAll().stream()
                .filter(a -> a.getClaimId().equals(OPEN_CLAIM)).toList());
    }

    @Test
    void concurrentUploadsAtNineteenFilesAdmitExactlyOne() throws Exception {
        for (int i = 0; i < AttachmentRules.MAX_FILES_PER_CLAIM - 1; i++) {
            attachments.save(new Attachment(OPEN_CLAIM, "f" + i + ".pdf", "application/pdf", "cust-ana",
                    Instant.now(), AttachmentRulesTest.PDF));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(multipart("/api/claims/{id}/attachments", OPEN_CLAIM)
                                .file(new MockMultipartFile("file", "race.pdf", "application/pdf",
                                        AttachmentRulesTest.PDF))
                                .header("X-User-Id", "cust-ana").header("X-User-Role", "CUSTOMER"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        int tooLarge = 0;
        for (Future<Integer> f : results) {
            int status = f.get(30, TimeUnit.SECONDS);
            if (status == 201) created++;
            else if (status == 413) tooLarge++;
        }
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(THREADS - 1, tooLarge);
        assertEquals(AttachmentRules.MAX_FILES_PER_CLAIM, attachments.countByClaimId(OPEN_CLAIM));
    }
}