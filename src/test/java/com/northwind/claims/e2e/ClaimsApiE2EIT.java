package com.northwind.claims.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Starts the service on a real port and calls the claims API over HTTP, the way the gateway does. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ClaimsApiE2EIT {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    private HttpResponse<String> call(String path, String userId, String role) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static DocumentContext json(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
        return JsonPath.parse(response.body());
    }

    @Test
    void customerListsAndReadsOwnClaims() throws Exception {
        HttpResponse<String> list = call("/api/claims", "cust-ana", "CUSTOMER");
        assertThat(list.statusCode()).isEqualTo(200);
        List<String> ids = json(list).read("$[*].id");
        assertThat(ids).containsExactlyInAnyOrder("CLM-1001", "CLM-1002");

        HttpResponse<String> claim = call("/api/claims/CLM-1001", "cust-ana", "CUSTOMER");
        assertThat(claim.statusCode()).isEqualTo(200);
        DocumentContext body = json(claim);
        assertThat(body.read("$.claim.id", String.class)).isEqualTo("CLM-1001");
        assertThat(body.read("$.payableCents", Long.class)).isEqualTo(1_000_000L);
    }

    @Test
    void customerGets404ForAnotherCustomersClaim() throws Exception {
        assertThat(call("/api/claims/CLM-2001", "cust-ana", "CUSTOMER").statusCode()).isEqualTo(404);
    }

    @Test
    void adjusterListsAndReadsEveryClaim() throws Exception {
        HttpResponse<String> list = call("/api/claims", "adj-1", "ADJUSTER");
        assertThat(list.statusCode()).isEqualTo(200);
        List<String> ids = json(list).read("$[*].id");
        assertThat(ids).containsExactlyInAnyOrder("CLM-1001", "CLM-1002", "CLM-2001");

        HttpResponse<String> claim = call("/api/claims/CLM-2001", "adj-1", "ADJUSTER");
        assertThat(claim.statusCode()).isEqualTo(200);
        DocumentContext body = json(claim);
        assertThat(body.read("$.claim.customerId", String.class)).isEqualTo("cust-ben");
        assertThat(body.read("$.payableCents", Long.class)).isEqualTo(2_000_000L);
    }

    @Test
    void adjusterGets404ForUnknownClaim() throws Exception {
        assertThat(call("/api/claims/CLM-9999", "adj-1", "ADJUSTER").statusCode()).isEqualTo(404);
    }

    @Test
    void callsWithoutGatewayIdentityAreRejected() throws Exception {
        assertThat(call("/api/claims", null, null).statusCode()).isEqualTo(401);
        assertThat(call("/api/claims", "cust-ana", "ROOT").statusCode()).isEqualTo(401);
    }
}
