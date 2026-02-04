package it.demo.fabrick.unit.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

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
		// Use compareTo for BigDecimal comparison
		assertEquals(0, dto.getPayload().getBalance().compareTo(new BigDecimal("1000.50")));
		assertEquals(0, dto.getPayload().getAvailableBalance().compareTo(new BigDecimal("850.25")));
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
		// Use compareTo for BigDecimal comparison
		assertEquals(0, dto.getPayload().getList().get(0).getAmount().compareTo(new BigDecimal("100.00")));
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
