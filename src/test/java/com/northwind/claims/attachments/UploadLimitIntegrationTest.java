package com.northwind.claims.attachments;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** MockMvc skips the servlet container's multipart limits, so they are checked against a real server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadLimitIntegrationTest {
    @LocalServerPort
    int port;

    @Test
    void containerRejectsFilesOverTenMegabytesWith413() throws Exception {
        assertEquals(413, uploadPdfOfSize(AttachmentRules.MAX_FILE_BYTES + 1).statusCode());
    }

    @Test
    void containerAcceptsFilesOfExactlyTenMegabytes() throws Exception {
        assertEquals(201, uploadPdfOfSize(AttachmentRules.MAX_FILE_BYTES).statusCode());
    }

    private HttpResponse<String> uploadPdfOfSize(long size) throws Exception {
        byte[] big = new byte[(int) size];
        System.arraycopy(AttachmentRulesTest.PDF, 0, big, 0, AttachmentRulesTest.PDF.length);
        String boundary = "test-boundary-000";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(big);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/claims/CLM-1002/attachments"))
                .header("X-User-Id", "cust-ana")
                .header("X-User-Role", "CUSTOMER")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
