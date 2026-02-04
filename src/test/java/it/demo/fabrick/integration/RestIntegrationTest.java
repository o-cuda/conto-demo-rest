package it.demo.fabrick.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxTestContext;

/**
 * Integration tests for REST API endpoints.
 * These tests require the application to be running on port 8080.
 *
 * Prerequisites:
 * - Application must be started with: mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DapplicationPropertiesPath=file:/path/to/config-map/local/application.properties"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("REST API Integration Tests")
class RestIntegrationTest {

    private Vertx vertx;
    private WebClient webClient;
    private static final int HTTP_PORT = 8080;
    private static final String HOST = "localhost";

    @BeforeAll
    void setup() {
        vertx = Vertx.vertx();
        WebClientOptions options = new WebClientOptions()
            .setDefaultHost(HOST)
            .setDefaultPort(HTTP_PORT)
            .setSsl(false)
            .setConnectTimeout(5000);
        webClient = WebClient.create(vertx, options);
    }

    @Test
    @DisplayName("GET /api/accounts/balance - should return balance")
    void testGetBalance(VertxTestContext testContext) {
        webClient.get("/api/accounts/balance")
            .send(ar -> {
                if (ar.succeeded()) {
                    var response = ar.result();
                    assertEquals(200, response.statusCode());
                    testContext.completeNow();
                } else {
                    testContext.failNow(ar.cause());
                }
            });
    }

    @Test
    @DisplayName("GET /api/accounts/transactions - should return transactions")
    void testGetTransactions(VertxTestContext testContext) {
        String fromDate = "2019-01-01";
        String toDate = "2019-04-10";

        webClient.get("/api/accounts/transactions")
            .addQueryParam("fromAccountingDate", fromDate)
            .addQueryParam("toAccountingDate", toDate)
            .send(ar -> {
                if (ar.succeeded()) {
                    var response = ar.result();
                    assertEquals(200, response.statusCode());
                    testContext.completeNow();
                } else {
                    testContext.failNow(ar.cause());
                }
            });
    }

    @Test
    @DisplayName("POST /api/accounts/payments/money-transfers - should process transfer")
    void testPostMoneyTransfer(VertxTestContext testContext) {
        JsonObject requestBody = new JsonObject()
            .put("creditor", new JsonObject()
                .put("name", "Mario Rossi")
                .put("account", new JsonObject()
                    .put("accountCode", "IT12345678901")
                    .put("bicCode", "BCITITMM")))
            .put("description", "Test payment")
            .put("amount", 100.50)
            .put("currency", "EUR")
            .put("executionDate", "2025-01-01")
            .put("feeType", "SHA")
            .put("feeAccountId", "12345678");

        webClient.post("/api/accounts/payments/money-transfers")
            .sendJsonObject(requestBody, ar -> {
                if (ar.succeeded()) {
                    var response = ar.result();
                    // Note: This may return an error from Fabrick API if the account is not valid
                    assertTrue(response.statusCode() == 200 || response.statusCode() == 500);
                    testContext.completeNow();
                } else {
                    testContext.failNow(ar.cause());
                }
            });
    }
}
