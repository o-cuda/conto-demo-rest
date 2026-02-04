package it.demo.fabrick.utils;

/**
 * Constants for Vert.x event bus addresses.
 * Event bus addresses follow the naming convention: {operation}_bus
 */
public class EventBusConstants {

    private EventBusConstants() {
    }

    /** Event bus address for balance operations */
    public static final String SALDO_BUS = "saldo_bus";

    /** Event bus address for transaction list operations */
    public static final String LISTA_BUS = "lista_bus";

    /** Event bus address for money transfer operations */
    public static final String BONIFICO_BUS = "bonifico_bus";

    /** Event bus address for transaction persistence operations */
    public static final String TRANSACTION_PERSISTENCE_BUS = "transaction_persistence_bus";

}
