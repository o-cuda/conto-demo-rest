package it.demo.fabrick.error;

/**
 * Semantic error codes for event bus message failures.
 * These codes distinguish between different types of errors for proper handling.
 */
public final class ErrorCode {

    private ErrorCode() {
        // Utility class - prevent instantiation
    }

    // Client validation errors (4xx equivalent)
    /** Invalid request body format or structure (JSON parsing error) */
    public static final int VALIDATION_INVALID_REQUEST = 400;

    /** Missing required request parameters or fields */
    public static final int VALIDATION_MISSING_PARAMETER = 401;

    /** Invalid parameter value (e.g., invalid date format) */
    public static final int VALIDATION_INVALID_VALUE = 402;

    // External API errors (5xx equivalent)
    /** External API returned an error response */
    public static final int API_ERROR = 500;

    /** External API connection failure or timeout */
    public static final int API_CONNECTION_FAILED = 501;

    /** Error parsing external API response */
    public static final int API_PARSE_ERROR = 502;

    // Internal errors
    /** JSON serialization error */
    public static final int INTERNAL_SERIALIZATION_ERROR = 600;

    /** Internal processing error */
    public static final int INTERNAL_ERROR = 601;

    /**
     * Get the HTTP status code equivalent for an error code.
     *
     * @param errorCode the error code
     * @return the HTTP status code (400 for 4xx errors, 500 for 5xx errors)
     */
    public static int toHttpStatusCode(int errorCode) {
        if (errorCode >= 400 && errorCode < 500) {
            return 400;
        } else if (errorCode >= 500 && errorCode < 600) {
            return 502; // Bad Gateway - external API error
        } else {
            return 500; // Internal Server Error
        }
    }
}
