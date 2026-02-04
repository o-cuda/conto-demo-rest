package it.demo.fabrick.unit.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import it.demo.fabrick.dto.BalanceDto;
import it.demo.fabrick.dto.ConfigurazioneDto;
import it.demo.fabrick.dto.TransactionDto;

/**
 * Unit tests for DTO serialization/deserialization.
 * Tests Jackson JSON conversion for all DTOs.
 */
@DisplayName("DTO Serialization Tests")
class DtoSerializationTest {

	private final ObjectMapper mapper = new ObjectMapper();

	// ==================== ConfigurazioneDto Tests ====================

	@Test
	@DisplayName("ConfigurazioneDto - constructor from JsonArray")
	void testConfigurazioneDto_fromJsonArray() {
		JsonArray jsonArray = new JsonArray()
			.add("LIS")
			.add("prod")
			.add("operazione=3;accountNumber=10")
			.add("lista_bus")
			.add("https://api.example.com/accounts/{accountNumber}/transactions");

		ConfigurazioneDto dto = new ConfigurazioneDto(jsonArray);

		assertEquals("LIS", dto.getOperation());
		assertEquals("prod", dto.getAmbiente());
		assertEquals("operazione=3;accountNumber=10", dto.getMessageIn());
		assertEquals("lista_bus", dto.getMessageOutFromBus());
		assertEquals("https://api.example.com/accounts/{accountNumber}/transactions", dto.getIndirizzo());
	}

	@Test
	@DisplayName("ConfigurazioneDto - toJsonArray conversion")
	void testConfigurazioneDto_toJsonArray() {
		JsonArray inputArray = new JsonArray()
			.add("BON")
			.add("test")
			.add("amount=15;currency=3")
			.add("bonifico_bus")
			.add("https://api.example.com/transfers");

		ConfigurazioneDto dto = new ConfigurazioneDto(inputArray);
		JsonArray jsonArray = dto.toJsonArray();

		assertEquals("BON", jsonArray.getString(0));
		assertEquals("test", jsonArray.getString(1));
		assertEquals("amount=15;currency=3", jsonArray.getString(2));
		assertEquals("bonifico_bus", jsonArray.getString(3));
		assertEquals("https://api.example.com/transfers", jsonArray.getString(4));
	}

	@Test
	@DisplayName("ConfigurazioneDto - round-trip conversion")
	void testConfigurazioneDto_roundTrip() {
		JsonArray original = new JsonArray()
			.add("SAL")
			.add("prod")
			.add("account=10")
			.add("saldo_bus")
			.add("https://api.example.com/balance");

		ConfigurazioneDto dto = new ConfigurazioneDto(original);
		JsonArray converted = dto.toJsonArray();

		assertEquals(original.getString(0), converted.getString(0));
		assertEquals(original.getString(1), converted.getString(1));
		assertEquals(original.getString(2), converted.getString(2));
		assertEquals(original.getString(3), converted.getString(3));
		assertEquals(original.getString(4), converted.getString(4));
	}

	// ==================== BalanceDto Tests ====================

	@Test
	@DisplayName("BalanceDto - JSON deserialization")
	void testBalanceDto_deserialization() throws JsonProcessingException {
		String json = "{"
			+ "\"status\":\"OK\","
			+ "\"payload\":{"
			+ "\"date\":\"2023-01-15\","
			+ "\"balance\":1000.50,"
			+ "\"availableBalance\":850.25,"
			+ "\"currency\":\"EUR\""
			+ "}"
			+ "}";

		BalanceDto dto = mapper.readValue(json, BalanceDto.class);

		assertEquals("OK", dto.getStatus());
		assertEquals("2023-01-15", dto.getPayload().getDate());
		assertEquals(1000.50, dto.getPayload().getBalance());
		assertEquals(850.25, dto.getPayload().getAvailableBalance());
		assertEquals("EUR", dto.getPayload().getCurrency());
	}

	// ==================== TransactionDto Tests ====================

	@Test
	@DisplayName("TransactionDto - JSON deserialization")
	void testTransactionDto_deserialization() throws JsonProcessingException {
		String json = "{"
			+ "\"status\":\"OK\","
			+ "\"payload\":{"
			+ "\"list\":["
			+ "{\"transactionId\":\"T001\",\"amount\":100.00},"
			+ "{\"transactionId\":\"T002\",\"amount\":-50.00}"
			+ "]"
			+ "}"
			+ "}";

		TransactionDto dto = mapper.readValue(json, TransactionDto.class);

		assertEquals("OK", dto.getStatus());
		assertEquals(2, dto.getPayload().getList().size());
		assertEquals("T001", dto.getPayload().getList().get(0).getTransactionId());
		assertEquals(100.00, dto.getPayload().getList().get(0).getAmount());
	}

	@Test
	@DisplayName("TransactionDto - empty transaction list")
	void testTransactionDto_emptyList() throws JsonProcessingException {
		String json = "{"
			+ "\"status\":\"OK\","
			+ "\"payload\":{"
			+ "\"list\":[]"
			+ "}"
			+ "}";

		TransactionDto dto = mapper.readValue(json, TransactionDto.class);

		assertEquals("OK", dto.getStatus());
		assertEquals(0, dto.getPayload().getList().size());
	}
}
