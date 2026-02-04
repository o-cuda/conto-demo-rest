package it.demo.fabrick.utils;

/**
 * Constants for status codes and messages used throughout the application.
 */
public class StatusConstants {

    private StatusConstants() {
    }

    // ==================== Status Values ====================

    /** Status value for successful operations */
    public static final String OK = "OK";

    /** Status value for failed operations */
    public static final String ERROR = "ERROR";

    /** Transfer status - pending execution */
    public static final String STATUS_PENDING = "PENDING";

    /** Transfer status - executed successfully */
    public static final String STATUS_EXECUTED = "EXECUTED";

    /** Transfer status - canceled */
    public static final String STATUS_CANCELED = "CANCELED";

    // ==================== Error Messages ====================

    /** Error message when OpenAPI specification is not found */
    public static final String ERROR_OPENAPI_NOT_FOUND = "OpenAPI specification not found";

    /** Prefix for error reading OpenAPI specification */
    public static final String ERROR_OPENAPI_READ_PREFIX = "Error reading OpenAPI specification: ";

    /** Error message when account ID cannot be extracted from URL */
    public static final String ERROR_CANNOT_EXTRACT_ACCOUNT_ID = "Could not extract account ID for validation enquiry";

    /** Error message for unknown transfer status after failed validation enquiry */
    public static final String ERROR_VALIDATION_ENQUIRY_FAILED = "Transfer status unknown - validation enquiry failed. Please retry later.";

    /** Error message when transfer validation finds no matching transaction */
    public static final String ERROR_NO_MATCHING_TRANSACTION = "Transfer failed - no matching transaction found. Please retry.";

    /** Error message for unknown transfer status due to parsing error */
    public static final String ERROR_VALIDATION_PARSING_FAILED = "Transfer status unknown - error parsing validation enquiry";

    /** Error message for unknown transfer status when validation enquiry is unavailable */
    public static final String ERROR_VALIDATION_UNAVAILABLE = "Transfer status unknown - validation enquiry unavailable. Please retry later.";

    // ==================== JSON Field Names ====================

    /** JSON field name for remittance information in Fabrick API */
    public static final String FIELD_REMITTANCE_INFORMATION = "REMITTANCE_INFORMATION";

}
