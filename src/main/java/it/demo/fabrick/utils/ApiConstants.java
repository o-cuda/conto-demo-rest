package it.demo.fabrick.utils;

/**
 * Constants for Fabrick API endpoint URLs.
 * All API endpoints follow the pattern: https://sandbox.platfr.io/api/gbs/banking/v4.0/...
 */
public class ApiConstants {

    private ApiConstants() {
    }

    /** Base URL for Fabrick API (SVIL environment) */
    public static final String FABRICK_API_BASE = "https://sandbox.platfr.io/api/gbs/banking/v4.0";

    /** Balance endpoint template: {accountId} will be replaced with actual account ID */
    public static final String BALANCE_URL_TEMPLATE =
        FABRICK_API_BASE + "/accounts/{accountId}/balance";

    /** Transactions endpoint template: {accountId}, {fromDate}, {toDate} will be replaced */
    public static final String TRANSACTIONS_URL_TEMPLATE =
        FABRICK_API_BASE + "/accounts/{accountId}/transactions?fromAccountingDate={fromDate}&toAccountingDate={toDate}";

    /** Money transfer endpoint template: {accountId} will be replaced with actual account ID */
    public static final String MONEY_TRANSFER_URL_TEMPLATE =
        FABRICK_API_BASE + "/accounts/{accountId}/payments/money-transfers";

    /** Transactions URL format for validation enquiry: uses String.format() */
    public static final String TRANSACTIONS_URL_FORMAT =
        FABRICK_API_BASE + "/accounts/%s/transactions?fromAccountingDate=%s&toAccountingDate=%s";

    /** REST API base path */
    public static final String REST_API_BASE = "/api/accounts";

    /** REST API endpoints */
    public static final String REST_BALANCE_ENDPOINT = REST_API_BASE + "/balance";
    public static final String REST_TRANSACTIONS_ENDPOINT = REST_API_BASE + "/transactions";
    public static final String REST_MONEY_TRANSFER_ENDPOINT = REST_API_BASE + "/payments/money-transfers";

}
