package it.demo.fabrick.vertx;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.reactiverse.contextual.logging.ContextualData;
import it.demo.fabrick.ContoDemoApplication;
import it.demo.fabrick.dto.rest.BonificoRestRequestDto;
import it.demo.fabrick.error.ErrorCode;
import it.demo.fabrick.utils.ApiConstants;
import it.demo.fabrick.utils.EventBusConstants;
import it.demo.fabrick.utils.StatusConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP server verticle that exposes REST endpoints for banking operations
 * Replaces the TCP socket server
 */
@Component
@Slf4j
public class HttpServerVerticle extends AbstractVerticle {

    private final ObjectMapper objectMapper;
    private final int httpPort;
    private final String accountId;
    private final Validator validator;

    public HttpServerVerticle(ObjectMapper objectMapper,
                              @Value("${http.server.port:8080}") int httpPort,
                              @Value("${fabrick.accountId}") String accountId,
                              Validator validator) {
        this.objectMapper = objectMapper;
        this.httpPort = httpPort;
        this.accountId = accountId;
        this.validator = validator;
    }

    // API endpoints from ApiConstants

    @Override
    public void start(Promise<Void> startFuture) {
        log.info("start - launching HTTP server on port {}", httpPort);

        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // Enable body handling for POST requests
        router.route().handler(BodyHandler.create());

        // Register API documentation endpoints
        router.get("/openapi.yaml").handler(this::serveOpenApiYaml);
        router.get("/openapi.json").handler(this::serveOpenApiJson);
        router.get("/swagger").handler(this::serveSwaggerUi);

        // Register business endpoints
        router.get(ApiConstants.REST_BALANCE_ENDPOINT).handler(this::handleBalance);
        router.get(ApiConstants.REST_TRANSACTIONS_ENDPOINT).handler(this::handleTransactions);
        router.post(ApiConstants.REST_MONEY_TRANSFER_ENDPOINT).handler(this::handleMoneyTransfer);

        server.requestHandler(router).listen(httpPort, http -> {
            if (http.succeeded()) {
                log.info("HTTP server started on port {}", httpPort);
                log.info("API Documentation available at: http://localhost:{}/swagger", httpPort);
                startFuture.complete();
            } else {
                log.error("Failed to start HTTP server", http.cause());
                startFuture.fail(http.cause());
            }
        });
    }

    /**
     * GET /api/accounts/balance
     */
    private void handleBalance(RoutingContext ctx) {
        String requestId = java.util.UUID.randomUUID().toString();
        ContextualData.put("requestId", requestId);

        log.info("Received balance request for accountId: {}", accountId);

        String apiUrl = ApiConstants.BALANCE_URL_TEMPLATE.replace("{accountId}", accountId);

        JsonObject message = new JsonObject()
            .put("accountId", accountId)
            .put("indirizzo", apiUrl)
            .put("requestId", requestId);

        vertx.eventBus().request(EventBusConstants.SALDO_BUS, message,
            ContoDemoApplication.getDefaultDeliverOptions(), ar -> {
                if (ar.succeeded()) {
                    String result = (String) ar.result().body();
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(result);
                    log.info("Balance request completed successfully");
                } else {
                    log.error("Balance request failed for requestId: {}", requestId, ar.cause());
                    sendError(ctx, ar.cause(), requestId);
                }
            });
    }

    /**
     * GET /api/accounts/transactions
     */
    private void handleTransactions(RoutingContext ctx) {
        String requestId = java.util.UUID.randomUUID().toString();
        ContextualData.put("requestId", requestId);

        String fromDate = ctx.queryParams().get("fromAccountingDate");
        String toDate = ctx.queryParams().get("toAccountingDate");

        // Input validation
        if (fromDate == null || fromDate.trim().isEmpty()) {
            log.warn("Missing required parameter: fromAccountingDate for requestId: {}", requestId);
            sendValidationError(ctx, "Missing required parameter: fromAccountingDate", requestId);
            return;
        }
        if (toDate == null || toDate.trim().isEmpty()) {
            log.warn("Missing required parameter: toAccountingDate for requestId: {}", requestId);
            sendValidationError(ctx, "Missing required parameter: toAccountingDate", requestId);
            return;
        }

        // Validate date format (ISO date: YYYY-MM-DD)
        if (!fromDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            log.warn("Invalid date format for fromAccountingDate: {} for requestId: {}", fromDate, requestId);
            sendValidationError(ctx, "Invalid date format for fromAccountingDate, expected YYYY-MM-DD", requestId);
            return;
        }
        if (!toDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            log.warn("Invalid date format for toAccountingDate: {} for requestId: {}", toDate, requestId);
            sendValidationError(ctx, "Invalid date format for toAccountingDate, expected YYYY-MM-DD", requestId);
            return;
        }

        log.info("Received transactions request for accountId: {}, from: {}, to: {}",
            accountId, fromDate, toDate);

        String apiUrl = ApiConstants.TRANSACTIONS_URL_TEMPLATE
            .replace("{accountId}", accountId)
            .replace("{fromDate}", fromDate)
            .replace("{toDate}", toDate);

        JsonObject message = new JsonObject()
            .put("accountId", accountId)
            .put("indirizzo", apiUrl)
            .put("requestId", requestId);

        vertx.eventBus().request(EventBusConstants.LISTA_BUS, message,
            ContoDemoApplication.getDefaultDeliverOptions(), ar -> {
                if (ar.succeeded()) {
                    String result = (String) ar.result().body();
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(result);
                    log.info("Transactions request completed successfully");
                } else {
                    log.error("Transactions request failed for requestId: {}", requestId, ar.cause());
                    sendError(ctx, ar.cause(), requestId);
                }
            });
    }

    /**
     * POST /api/accounts/payments/money-transfers
     */
    private void handleMoneyTransfer(RoutingContext ctx) {
        String requestId = java.util.UUID.randomUUID().toString();
        ContextualData.put("requestId", requestId);

        String body = ctx.body().asString();

        log.info("Received money transfer request for accountId: {}", accountId);
        log.debug("Request body: {}", body);

        try {
            // Use Jackson ObjectMapper for reliable POJO deserialization
            ObjectMapper mapper = objectMapper;
            BonificoRestRequestDto request = mapper.readValue(body, BonificoRestRequestDto.class);

            // Bean Validation using Jakarta validation
            Set<ConstraintViolation<BonificoRestRequestDto>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String validationErrors = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
                log.warn("Validation failed for money transfer request: {} for requestId: {}", validationErrors, requestId);
                sendValidationError(ctx, "Validation failed: " + validationErrors, requestId);
                return;
            }

            String apiUrl = ApiConstants.MONEY_TRANSFER_URL_TEMPLATE.replace("{accountId}", accountId);

            // Convert the request object to JSON string for event bus transport
            String requestJson = mapper.writeValueAsString(request);

            JsonObject message = new JsonObject()
                .put("accountId", accountId)
                .put("indirizzo", apiUrl)
                .put("requestId", requestId)
                .put("request", requestJson);

            vertx.eventBus().request(EventBusConstants.BONIFICO_BUS, message,
                ContoDemoApplication.getDefaultDeliverOptions(), ar -> {
                    if (ar.succeeded()) {
                        String result = (String) ar.result().body();
                        ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(result);
                        log.info("Money transfer request completed successfully");
                    } else {
                        log.error("Money transfer request failed for requestId: {}", requestId, ar.cause());
                        sendError(ctx, ar.cause(), requestId);
                    }
                });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse request body for requestId: {}", requestId, e);
            sendValidationError(ctx, "Invalid JSON in request body: " + e.getOriginalMessage(), requestId);
        } catch (Exception e) {
            log.error("Unexpected error processing money transfer for requestId: {}", requestId, e);
            sendError(ctx, e, requestId);
        }
    }

    /**
     * Serve OpenAPI YAML specification
     * GET /openapi.yaml
     */
    private void serveOpenApiYaml(RoutingContext ctx) {
        try {
            // Read from classpath (works both in IDE and packaged JAR)
            InputStream is = getClass().getResourceAsStream("/openapi.yaml");
            if (is != null) {
                String yamlContent = new String(is.readAllBytes());
                log.info("Serving OpenAPI specification, length: {} bytes", yamlContent.length());
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/x-yaml")
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .end(yamlContent);
            } else {
                log.error("OpenAPI specification not found in classpath");
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("status", StatusConstants.ERROR)
                        .put("message", StatusConstants.ERROR_OPENAPI_NOT_FOUND)
                        .encode());
            }
        } catch (Exception e) {
            log.error("Error reading OpenAPI specification", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("status", StatusConstants.ERROR)
                    .put("message", StatusConstants.ERROR_OPENAPI_READ_PREFIX + e.getMessage())
                    .encode());
        }
    }

    /**
     * Serve OpenAPI JSON specification
     * GET /openapi.json
     */
    private void serveOpenApiJson(RoutingContext ctx) {
        // For now, just return a message to use the YAML endpoint
        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end("{\"message\":\"Please use /openapi.yaml for the specification\"}");
    }

    /**
     * Serve Swagger UI HTML page
     * GET /swagger
     */
    private void serveSwaggerUi(RoutingContext ctx) {
        String swaggerHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Conto Demo Banking API - Swagger UI</title>
                <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui.css">
                <style>
                    body { margin: 0; padding: 0; }
                    #swagger-ui { max-width: 1460px; margin: 0 auto; }
                </style>
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui-bundle.js"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui-standalone-preset.js"></script>
                <script>
                    window.onload = function() {
                        const ui = SwaggerUIBundle({
                            url: '/openapi.yaml',
                            dom_id: '#swagger-ui',
                            deepLinking: true,
                            presets: [
                                SwaggerUIBundle.presets.apis,
                                SwaggerUIStandalonePreset
                            ],
                            plugins: [
                                SwaggerUIBundle.plugins.DownloadUrl
                            ],
                            layout: "StandaloneLayout",
                            defaultModelsExpandDepth: 1,
                            defaultModelExpandDepth: 1,
                            tryItOutEnabled: true
                        });
                        window.ui = ui;
                    };
                </script>
            </body>
            </html>
            """;

        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "text/html")
            .end(swaggerHtml);
    }

    /**
     * Send error response with appropriate HTTP status code based on the cause.
     */
    private void sendError(RoutingContext ctx, Throwable cause, String requestId) {
        String errorMessage = cause != null ? cause.getMessage() : "Unknown error";
        int httpStatusCode = 500;

        // Determine HTTP status code from error message if it contains an error code
        if (cause != null && errorMessage != null) {
            // Check if error message contains our error code prefix (e.g., "ErrorCode 402")
            if (errorMessage.matches(".*\\b(40[0-9]|50[0-9]|60[0-9])\\b.*")) {
                // Extract error code from message
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(40[0-9]|50[0-9]|60[0-9])\\b")
                    .matcher(errorMessage);
                if (matcher.find()) {
                    int errorCode = Integer.parseInt(matcher.group(1));
                    httpStatusCode = ErrorCode.toHttpStatusCode(errorCode);
                }
            }
        }

        JsonObject errorResponse = new JsonObject()
            .put("status", StatusConstants.ERROR)
            .put("requestId", requestId)
            .put("message", errorMessage);

        ctx.response()
            .setStatusCode(httpStatusCode)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }

    /**
     * Send validation error response with HTTP 400 status code.
     */
    private void sendValidationError(RoutingContext ctx, String errorMessage, String requestId) {
        JsonObject errorResponse = new JsonObject()
            .put("status", StatusConstants.ERROR)
            .put("requestId", requestId)
            .put("message", errorMessage);

        ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }
}
