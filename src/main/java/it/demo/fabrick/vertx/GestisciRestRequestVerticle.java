package it.demo.fabrick.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import it.demo.fabrick.ContoDemoApplication;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verticle that handles REST request routing to operation verticles
 * Replaces GestisciRequestVerticle (positional message parsing)
 * Uses hardcoded API endpoints instead of database configuration
 */
// @Component - Not used, HttpServerVerticle directly routes to operation verticles
@Slf4j
public class GestisciRestRequestVerticle extends AbstractVerticle {

    // Hardcoded API endpoints (from CONTO_INDIRIZZI table for SVIL environment)
    private static final String SALDO_URL_TEMPLATE =
        "https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/{accountId}/balance";

    private static final String TRANSACTIONS_URL_TEMPLATE =
        "https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/{accountId}/transactions?fromAccountingDate={fromDate}&toAccountingDate={toDate}";

    private static final String BONIFICO_URL_TEMPLATE =
        "https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/{accountId}/payments/money-transfers";

    private static final String REST_REQUEST_BUS = "rest_request_bus";

    @Override
    public void start(Promise<Void> startFuture) {
        log.info("start - launched");

        // Subscribe to REST request events from HttpServerVerticle
        vertx.eventBus().consumer(REST_REQUEST_BUS, this::handleRestRequest);

        log.info("Subscribed to event bus: {}", REST_REQUEST_BUS);
        startFuture.complete();
    }

    private void handleRestRequest(Message<Object> message) {
        log.info("handleRestRequest - received request");

        JsonObject json = (JsonObject) message.body();
        String operation = json.getString("operation");

        log.debug("Operation type: {}", operation);

        String targetBus;
        String apiUrl;

        // Route to appropriate verticle based on operation type
        switch (operation) {
            case "SAL":
                targetBus = "saldo_bus";
                String accountIdSaldo = json.getString("accountId");
                apiUrl = SALDO_URL_TEMPLATE.replace("{accountId}", accountIdSaldo);
                break;

            case "LIS":
                targetBus = "lista_bus";
                String accountIdList = json.getString("accountId");
                String fromDate = json.getString("fromDate");
                String toDate = json.getString("toDate");
                apiUrl = TRANSACTIONS_URL_TEMPLATE
                    .replace("{accountId}", accountIdList)
                    .replace("{fromDate}", fromDate)
                    .replace("{toDate}", toDate);
                break;

            case "BON":
                targetBus = "bonifico_bus";
                String accountIdBonifico = json.getString("accountId");
                apiUrl = BONIFICO_URL_TEMPLATE.replace("{accountId}", accountIdBonifico);
                break;

            default:
                String errorMsg = "Unknown operation: " + operation;
                log.error(errorMsg);
                message.fail(1, errorMsg);
                return;
        }

        log.info("Routing to bus: {} with URL: {}", targetBus, apiUrl);

        // Forward to operation verticle with API URL
        JsonObject requestMessage = json.copy();
        requestMessage.put("indirizzo", apiUrl);

        vertx.eventBus().request(targetBus, requestMessage,
            ContoDemoApplication.getDefaultDeliverOptions(), ar -> {
                if (ar.succeeded()) {
                    message.reply(ar.result().body());
                } else {
                    log.error("Request to {} failed", targetBus, ar.cause());
                    message.fail(1, ar.cause().getMessage());
                }
            });
    }
}
