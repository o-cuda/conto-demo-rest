package it.demo.fabrick.vertx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import it.demo.fabrick.dto.ListaTransactionDto;
import lombok.extern.slf4j.Slf4j;

/**
 * Verticle for asynchronously persisting transactions to H2 database using Vert.x JDBCClient.
 * Listens on the event bus for transaction persistence requests.
 *
 * This verticle ensures that:
 * 1. Duplicate transactions (by transactionId) are not inserted
 * 2. Database operations run asynchronously using Vert.x JDBCClient
 * 3. Single SELECT with IN clause checks all transactionIds at once
 * 4. Single batch INSERT inserts all new transactions at once
 * 5. The REST API responds immediately while persistence happens in the background
 */
@Component
@Slf4j
public class TransactionPersistenceVerticle extends AbstractVerticle {

	private JDBCClient jdbcClient;
	private final String dbUrl;
	private final String driverClassName;
	private final String username;
	private final String password;

	public TransactionPersistenceVerticle(
			@Value("${spring.datasource.url}") String dbUrl,
			@Value("${spring.datasource.driverClassName}") String driverClassName,
			@Value("${spring.datasource.username:}") String username,
			@Value("${spring.datasource.password:}") String password) {

		this.dbUrl = dbUrl;
		this.driverClassName = driverClassName;
		this.username = username;
		this.password = password;
	}

	@Override
	public void start(Promise<Void> startFuture) throws Exception {
		log.info("start - TransactionPersistenceVerticle started");

		// Create JDBCClient here where vertx instance is available
		JsonObject config = new JsonObject()
			.put("url", dbUrl)
			.put("driver_class", driverClassName)
			.put("user", username)
			.put("password", password);

		this.jdbcClient = JDBCClient.createShared(vertx, config);

		String bus = "transaction_persistence_bus";
		log.debug("Subscribing to event bus address: '{}' ..", bus);
		vertx.eventBus().consumer(bus, this::handlePersistenceRequest);
		log.info("TransactionPersistenceVerticle ready to persist transactions");
		startFuture.complete();
	}

	/**
	 * Handle transaction persistence requests from the event bus.
	 *
	 * Expected message format:
	 * {
	 *   "transactions": [ListaTransactionDto objects as JSON],
	 *   "requestId": "uuid"
	 * }
	 *
	 * @param message the event bus message containing transaction data
	 */
	public void handlePersistenceRequest(Message<Object> message) {
		log.info("handlePersistenceRequest - Received persistence request");

		JsonObject json = (JsonObject) message.body();
		String requestId = json.getString("requestId");

		// Extract the transactions array from the message
		Object transactionsObj = json.getValue("transactions");
		if (!(transactionsObj instanceof JsonArray)) {
			log.warn("No transactions found in persistence request for requestId: {}", requestId);
			return;
		}

		JsonArray transactionsArray = (JsonArray) transactionsObj;

		log.info("Processing {} transactions for persistence, requestId: {}", transactionsArray.size(), requestId);

		// Convert JSON to ListaTransactionDto list
		List<ListaTransactionDto> transactions = transactionsArray.stream()
			.map(obj -> (JsonObject) obj)
			.map(this::mapJsonToListaTransactionDto)
			.collect(Collectors.toList());

		// Perform async persistence using Vert.x JDBCClient
		persistTransactions(transactions, requestId);
	}

	/**
	 * Persist transactions to the database using optimized SQL.
	 * 1. Single SELECT with IN clause to check which transactionIds already exist
	 * 2. Single batch INSERT to insert all new transactions at once
	 *
	 * @param transactions the list of transactions to persist
	 * @param requestId the request ID for logging
	 */
	private void persistTransactions(List<ListaTransactionDto> transactions, String requestId) {
		if (transactions.isEmpty()) {
			log.debug("No transactions to persist for requestId: {}", requestId);
			return;
		}

		// Step 1: Check existing transactionIds with a single SELECT using IN clause
		List<String> transactionIds = transactions.stream()
			.map(ListaTransactionDto::getTransactionId)
			.collect(Collectors.toList());

		String inClause = transactionIds.stream()
			.map(id -> "?")
			.collect(Collectors.joining(", "));

		String selectSql = "SELECT TRANSACTION_ID FROM CONTO_TRANSACTION WHERE TRANSACTION_ID IN (" + inClause + ")";

		JsonArray params = new JsonArray();
		transactionIds.forEach(params::add);

		jdbcClient.getConnection(ar -> {
			if (ar.failed()) {
				log.error("Failed to get database connection for requestId: {}", requestId, ar.cause());
				return;
			}

			SQLConnection conn = ar.result();

			// Execute SELECT to find existing transactionIds
			conn.queryWithParams(selectSql, params, queryRes -> {
				if (queryRes.failed()) {
					log.error("Failed to query existing transactions for requestId: {}", requestId, queryRes.cause());
					conn.close();
					return;
				}

				// Build set of existing transactionIds
				Set<String> existingIds = new HashSet<>();
				queryRes.result().getRows().forEach(row ->
					existingIds.add(row.getString("TRANSACTION_ID"))
				);

				log.debug("Found {} existing transactions for requestId: {}", existingIds.size(), requestId);

				// Step 2: Filter out existing transactions
				List<ListaTransactionDto> newTransactions = transactions.stream()
					.filter(t -> !existingIds.contains(t.getTransactionId()))
					.collect(Collectors.toList());

				if (newTransactions.isEmpty()) {
					log.info("All {} transactions already exist, skipping insert for requestId: {}",
						transactions.size(), requestId);
					conn.close();
					return;
				}

				log.info("Inserting {} new transactions (skipped {} existing) for requestId: {}",
					newTransactions.size(), existingIds.size(), requestId);

				// Step 3: Batch insert all new transactions
				batchInsertTransactions(conn, newTransactions, requestId);
			});
		});
	}

	/**
	 * Batch insert transactions using a single SQL statement.
	 *
	 * @param conn the SQL connection
	 * @param transactions the list of transactions to insert
	 * @param requestId the request ID for logging
	 */
	private void batchInsertTransactions(SQLConnection conn, List<ListaTransactionDto> transactions, String requestId) {
		// Build batch INSERT statement
		String insertSql = """
			INSERT INTO CONTO_TRANSACTION (
				TRANSACTION_ID, OPERATION_ID, ACCOUNTING_DATE, VALUE_DATE,
				TYPE_ENUMERATION, TYPE_VALUE, AMOUNT, CURRENCY, DESCRIPTION
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

		List<JsonArray> batchParams = new ArrayList<>();

		for (ListaTransactionDto dto : transactions) {
			JsonArray params = new JsonArray()
				.add(dto.getTransactionId())
				.add(dto.getOperationId())
				.add(dto.getAccountingDate())
				.add(dto.getValueDate());

			// Handle nested type object
			if (dto.getType() != null) {
				params.add(dto.getType().getEnumeration())
					.add(dto.getType().getValue());
			} else {
				params.addNull().addNull();
			}

			params.add(dto.getAmount())
				.add(dto.getCurrency())
				.add(dto.getDescription());

			batchParams.add(params);
		}

		// Execute batch insert
		conn.batchWithParams(insertSql, batchParams, batchRes -> {
			conn.close();
			if (batchRes.succeeded()) {
				List<Integer> results = batchRes.result();
				int totalAffected = results.stream().mapToInt(Integer::intValue).sum();
				log.info("Batch insert completed for requestId: {} - Inserted: {} transactions",
					requestId, totalAffected);
			} else {
				log.error("Batch insert failed for requestId: {}", requestId, batchRes.cause());
			}
		});
	}

	/**
	 * Map a JsonObject to ListaTransactionDto.
	 *
	 * @param json the JSON object representing a transaction
	 * @return ListaTransactionDto
	 */
	private ListaTransactionDto mapJsonToListaTransactionDto(JsonObject json) {
		ListaTransactionDto dto = new ListaTransactionDto();

		dto.setTransactionId(json.getString("transactionId"));
		dto.setOperationId(json.getString("operationId"));
		dto.setAccountingDate(json.getString("accountingDate"));
		dto.setValueDate(json.getString("valueDate"));
		dto.setAmount(json.getDouble("amount"));
		dto.setCurrency(json.getString("currency"));
		dto.setDescription(json.getString("description"));

		// Handle nested type object
		JsonObject typeJson = json.getJsonObject("type");
		if (typeJson != null) {
			ListaTransactionDto.Type type = new ListaTransactionDto().new Type();
			type.setEnumeration(typeJson.getString("enumeration"));
			type.setValue(typeJson.getString("value"));
			dto.setType(type);
		}

		return dto;
	}
}
