package it.demo.fabrick.unit.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonObject;
import it.demo.fabrick.dto.BalanceDto;
import it.demo.fabrick.dto.TransactionDto;

/**
 * Unit tests for DTO serialization/deserialization.
 * Tests Jackson JSON conversion for all DTOs.
 */
@DisplayName("DTO Serialization Tests")
class DtoSerializationTest {

	private final ObjectMapper mapper = new ObjectMapper();

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
