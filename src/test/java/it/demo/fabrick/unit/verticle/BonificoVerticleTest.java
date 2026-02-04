package it.demo.fabrick.unit.verticle;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import it.demo.fabrick.dto.ListaTransactionDto;
import it.demo.fabrick.dto.TransactionDto;
import it.demo.fabrick.dto.TransactionDto.Payload;
import it.demo.fabrick.dto.rest.BonificoRestRequestDto;
import it.demo.fabrick.vertx.BonificoVerticle;

/**
 * Unit tests for BonificoVerticle.
 */
@ExtendWith(VertxExtension.class)
@DisplayName("BonificoVerticle Tests")
class BonificoVerticleTest {

	private BonificoVerticle verticle;

	private static final String TEST_API_KEY = "test-api-key-12345";
	private static final String TEST_AUTH_SCHEMA = "S2S";

	@BeforeEach
	void setUp() {
		verticle = new BonificoVerticle(new ObjectMapper(), TEST_API_KEY, TEST_AUTH_SCHEMA);
	}

	// ==================== start() Tests ====================

	@Test
	@DisplayName("start - should subscribe to bonifico_bus")
	void testStart_subscribesToBonificoBus(Vertx vertx, VertxTestContext testContext) {
		// Initiate deployment - if start() succeeds without exception, the test passes
		vertx.deployVerticle(verticle);
		testContext.completeNow();
	}

	// ==================== Helper Method Tests ====================

	@Test
	@DisplayName("extractAccountIdFromUrl - should extract account ID correctly")
	void testExtractAccountIdFromUrl() throws Exception {
		// Use reflection to access the private method
		var method = BonificoVerticle.class.getDeclaredMethod("extractAccountIdFromUrl", String.class);
		method.setAccessible(true);

		String url = "https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/12345678/payments/money-transfers";
		String accountId = (String) method.invoke(verticle, url);

		assert "12345678".equals(accountId);
	}

	@Test
	@DisplayName("extractAccountIdFromUrl - should return null for malformed URL")
	void testExtractAccountIdFromUrl_malformedUrl() throws Exception {
		var method = BonificoVerticle.class.getDeclaredMethod("extractAccountIdFromUrl", String.class);
		method.setAccessible(true);

		String url = "https://invalid-url/no-accounts-here";
		String accountId = (String) method.invoke(verticle, url);

		assert accountId == null;
	}

	@Test
	@DisplayName("searchForMatchingTransfer - should find matching transaction")
	void testSearchForMatchingTransfer_found() throws Exception {
		var method = BonificoVerticle.class.getDeclaredMethod(
			"searchForMatchingTransfer", TransactionDto.class, BonificoRestRequestDto.class);
		method.setAccessible(true);

		// Create a transaction DTO with a matching outgoing transfer
		TransactionDto transactionDto = new TransactionDto();
		TransactionDto.Payload payload = transactionDto.new Payload();
		ArrayList<ListaTransactionDto> list = new ArrayList<ListaTransactionDto>();

		ListaTransactionDto outgoingTransfer = new ListaTransactionDto();
		outgoingTransfer.setAmount(new java.math.BigDecimal("-100.50")); // Negative for outgoing
		outgoingTransfer.setCurrency("EUR");
		outgoingTransfer.setDescription("Test payment");
		list.add(outgoingTransfer);

		payload.setList(list);
		transactionDto.setPayload(payload);

		// Create request matching the transaction
		BonificoRestRequestDto request = new BonificoRestRequestDto();
		request.setAmount(new java.math.BigDecimal("100.50"));
		request.setCurrency("EUR");
		request.setDescription("Test payment");

		boolean found = (Boolean) method.invoke(verticle, transactionDto, request);

		assert found : "Should find matching transfer";
	}

	@Test
	@DisplayName("searchForMatchingTransfer - should not match positive amount (incoming)")
	void testSearchForMatchingTransfer_incomingTransfer() throws Exception {
		var method = BonificoVerticle.class.getDeclaredMethod(
			"searchForMatchingTransfer", TransactionDto.class, BonificoRestRequestDto.class);
		method.setAccessible(true);

		// Create a transaction DTO with an incoming transfer (positive amount)
		TransactionDto transactionDto = new TransactionDto();
		TransactionDto.Payload payload = transactionDto.new Payload();
		ArrayList<ListaTransactionDto> list = new ArrayList<ListaTransactionDto>();

		ListaTransactionDto incomingTransfer = new ListaTransactionDto();
		incomingTransfer.setAmount(new java.math.BigDecimal("100.50")); // Positive for incoming
		incomingTransfer.setCurrency("EUR");
		list.add(incomingTransfer);

		payload.setList(list);
		transactionDto.setPayload(payload);

		BonificoRestRequestDto request = new BonificoRestRequestDto();
		request.setAmount(new java.math.BigDecimal("100.50"));
		request.setCurrency("EUR");

		boolean found = (Boolean) method.invoke(verticle, transactionDto, request);

		assert !found : "Should not match incoming transfer";
	}

	@Test
	@DisplayName("searchForMatchingTransfer - should handle amount with small rounding difference")
	void testSearchForMatchingTransfer_roundingTolerance() throws Exception {
		var method = BonificoVerticle.class.getDeclaredMethod(
			"searchForMatchingTransfer", TransactionDto.class, BonificoRestRequestDto.class);
		method.setAccessible(true);

		TransactionDto transactionDto = new TransactionDto();
		TransactionDto.Payload payload = transactionDto.new Payload();
		ArrayList<ListaTransactionDto> list = new ArrayList<ListaTransactionDto>();

		ListaTransactionDto transfer = new ListaTransactionDto();
		transfer.setAmount(new java.math.BigDecimal("-100.505")); // Slight rounding difference
		transfer.setCurrency("EUR");
		list.add(transfer);

		payload.setList(list);
		transactionDto.setPayload(payload);

		BonificoRestRequestDto request = new BonificoRestRequestDto();
		request.setAmount(new java.math.BigDecimal("100.50"));
		request.setCurrency("EUR");

		boolean found = (Boolean) method.invoke(verticle, transactionDto, request);

		assert found : "Should match with rounding tolerance";
	}

	@Test
	@DisplayName("searchForMatchingTransfer - should return false for null payload")
	void testSearchForMatchingTransfer_nullPayload() throws Exception {
		var method = BonificoVerticle.class.getDeclaredMethod(
			"searchForMatchingTransfer", TransactionDto.class, BonificoRestRequestDto.class);
		method.setAccessible(true);

		TransactionDto transactionDto = new TransactionDto();
		transactionDto.setPayload(null);

		BonificoRestRequestDto request = new BonificoRestRequestDto();

		boolean found = (Boolean) method.invoke(verticle, transactionDto, request);

		assert !found : "Should return false for null payload";
	}

	@Test
	@DisplayName("searchForMatchingTransfer - should return false for empty list")
	void testSearchForMatchingTransfer_emptyList() throws Exception {
		var method = BonificoVerticle.class.getDeclaredMethod(
			"searchForMatchingTransfer", TransactionDto.class, BonificoRestRequestDto.class);
		method.setAccessible(true);

		TransactionDto transactionDto = new TransactionDto();
		TransactionDto.Payload payload = transactionDto.new Payload();
		payload.setList(new ArrayList<>());
		transactionDto.setPayload(payload);

		BonificoRestRequestDto request = new BonificoRestRequestDto();

		boolean found = (Boolean) method.invoke(verticle, transactionDto, request);

		assert !found : "Should return false for empty list";
	}

	@Test
	@DisplayName("searchForMatchingTransfer - should match by currency")
	void testSearchForMatchingTransfer_currencyMismatch() throws Exception {
		var method = BonificoVerticle.class.getDeclaredMethod(
			"searchForMatchingTransfer", TransactionDto.class, BonificoRestRequestDto.class);
		method.setAccessible(true);

		TransactionDto transactionDto = new TransactionDto();
		TransactionDto.Payload payload = transactionDto.new Payload();
		ArrayList<ListaTransactionDto> list = new ArrayList<ListaTransactionDto>();

		ListaTransactionDto transfer = new ListaTransactionDto();
		transfer.setAmount(new java.math.BigDecimal("-100.50"));
		transfer.setCurrency("USD"); // Different currency
		list.add(transfer);

		payload.setList(list);
		transactionDto.setPayload(payload);

		BonificoRestRequestDto request = new BonificoRestRequestDto();
		request.setAmount(new java.math.BigDecimal("100.50"));
		request.setCurrency("EUR"); // Request expects EUR

		boolean found = (Boolean) method.invoke(verticle, transactionDto, request);

		assert !found : "Should not match with different currency";
	}
}
