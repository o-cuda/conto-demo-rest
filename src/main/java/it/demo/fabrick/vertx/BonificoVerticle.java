package it.demo.fabrick.vertx;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import it.demo.fabrick.dto.BonificoRequestDto;
import it.demo.fabrick.dto.ErrorDto;
import it.demo.fabrick.dto.ListaTransactionDto;
import it.demo.fabrick.dto.TransactionDto;
import it.demo.fabrick.dto.rest.BonificoRestRequestDto;
import it.demo.fabrick.dto.rest.BonificoRestResponseDto;
import it.demo.fabrick.mapper.DtoMapper;
import it.demo.fabrick.error.ErrorCode;
import it.demo.fabrick.utils.ApiConstants;
import it.demo.fabrick.utils.EventBusConstants;
import it.demo.fabrick.utils.StatusConstants;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BonificoVerticle extends AbstractVerticle {

	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String authSchema;

	public BonificoVerticle(ObjectMapper objectMapper,
							@Value("${fabrick.apiKey}") String apiKey,
							@Value("${fabrick.authSchema}") String authSchema) {
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.authSchema = authSchema;
	}

	@Override
	public void start(io.vertx.core.Promise<Void> startFuture) throws Exception {

		log.info("start - lanciato");

		String bus = EventBusConstants.BONIFICO_BUS;
		log.debug("mi sottoscrivo al bus '{}' ..", bus);
		vertx.eventBus().consumer(bus, message -> {

			lanciaChiamataEsterna(message);
		});
	}

	public void lanciaChiamataEsterna(Message<Object> message) {

		log.info("lanciaChiamataEsterna - start");

		JsonObject json = (JsonObject) message.body();

		final String indirizzo = json.getString("indirizzo");
		String requestId = json.getString("requestId");
		final String requestJson = json.getString("request");

		log.info("message.body().\"indirizzo\" = {}", indirizzo);
		log.info("message.body().\"requestId\" = {}", requestId);

		WebClient client = WebClient.create(vertx);

		ObjectMapper mapper = objectMapper;

		// Parse the REST request DTO first (outside the callback)
		final BonificoRestRequestDto restRequest;
		final String requestString;
		try {
			restRequest = mapper.readValue(requestJson, BonificoRestRequestDto.class);

			// Convert REST request DTO to Fabrick API request DTO
			BonificoRequestDto request = DtoMapper.toBonificoRequestDto(restRequest);
			requestString = mapper.writeValueAsString(request);
			log.debug("requestString: {}", requestString);
		} catch (JsonProcessingException e1) {
			String errorMessage = "ErrorCode " + ErrorCode.VALIDATION_INVALID_REQUEST + " - Error parsing request JSON";
			log.error(errorMessage, e1);
			message.fail(ErrorCode.VALIDATION_INVALID_REQUEST, errorMessage);
			return;
		}

		log.debug("richiamo servizio REST ...");
		client.requestAbs(HttpMethod.POST, indirizzo)
				.putHeader("Content-Type", "application/json")
				.putHeader("Auth-Schema", authSchema)
				.putHeader("Api-Key", apiKey)
				.putHeader("Content-Type", "application/json")
				.sendBuffer(Buffer.buffer(requestString), ar -> {
					if (ar.succeeded()) {

						HttpResponse<Buffer> response = ar.result();
						int statusCode = response.statusCode();
						@Nullable
						String bodyAsString = response.bodyAsString();

						log.info("Received response with status code: {}", statusCode);

						if (statusCode >= 300) {
							// Check if this is HTTP 500 or 504 - perform validation enquiry
							if (statusCode == 500 || statusCode == 504) {
								log.warn("Received HTTP {} - performing validation enquiry to verify transfer status", statusCode);
								performValidationEnquiry(message, restRequest, indirizzo);
								return;
							}

							ErrorDto errore = null;
							try {
								errore = mapper.readValue(bodyAsString, ErrorDto.class);
							} catch (JsonProcessingException e) {
								log.error("Error parsing error response from Fabrick API", e);
								message.fail(ErrorCode.API_PARSE_ERROR, "ErrorCode " + ErrorCode.API_PARSE_ERROR + " - Error parsing error response");
								return;
							}

							StringBuilder builder = new StringBuilder("ErrorCode " + ErrorCode.API_ERROR + " - ");
							errore.getErrors().stream().forEach(anError -> {
								builder.append("code: ").append(anError.getCode())
										.append(", description: ").append(anError.getDescription()).append("; ");
							});

							String messaggioDiErrore = builder.toString();
							log.error("Fabrick API error: {}", messaggioDiErrore);

							// Return error response
							BonificoRestResponseDto errorResponse = BonificoRestResponseDto.error(messaggioDiErrore);
							try {
								String errorJson = mapper.writeValueAsString(errorResponse);
								message.reply(errorJson);
							} catch (JsonProcessingException e) {
								log.error("Error serializing error response to JSON", e);
								message.fail(ErrorCode.INTERNAL_SERIALIZATION_ERROR, "ErrorCode " + ErrorCode.INTERNAL_SERIALIZATION_ERROR + " - Error serializing error response");
							}
							return;
						}

						log.info("bodyAsString: {}", bodyAsString);

						// Return success response
						BonificoRestResponseDto responseDto = BonificoRestResponseDto.success(StatusConstants.STATUS_PENDING);
						try {
							String jsonResponse = mapper.writeValueAsString(responseDto);
							message.reply(jsonResponse);
						} catch (JsonProcessingException e) {
							log.error("Error serializing success response to JSON", e);
							message.fail(ErrorCode.INTERNAL_SERIALIZATION_ERROR, "ErrorCode " + ErrorCode.INTERNAL_SERIALIZATION_ERROR + " - Error serializing success response");
						}

					} else {
						String errorMessage = String.format("ErrorCode %d - Unable to call Fabrick API, service may be down: %s",
							ErrorCode.API_CONNECTION_FAILED, ar.cause().getMessage());
						log.error(errorMessage, ar.cause());
						message.fail(ErrorCode.API_CONNECTION_FAILED, errorMessage);
					}
				});
	}

	/**
	 * Perform validation enquiry by searching transactions to verify if a money transfer was executed.
	 * Called when receiving HTTP 500 or HTTP 504 from money transfer endpoint.
	 *
	 * @param message the event bus message to reply to
	 * @param originalRequest the original money transfer request
	 * @param transferUrl the money transfer URL (used to extract accountId)
	 */
	private void performValidationEnquiry(Message<Object> message, BonificoRestRequestDto originalRequest, String transferUrl) {
		log.info("performValidationEnquiry - starting validation enquiry");

		// Extract accountId from the transfer URL
		// URL format: https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/{accountId}/payments/money-transfers
		String accountId = extractAccountIdFromUrl(transferUrl);
		if (accountId == null) {
			log.error("Could not extract accountId from URL: {}", transferUrl);
			sendErrorResponse(message, StatusConstants.ERROR_CANNOT_EXTRACT_ACCOUNT_ID);
			return;
		}

		// Use today's date for transaction search
		LocalDate today = LocalDate.now();
		String fromDate = today.toString();
		String toDate = today.toString();

		// Build transactions URL
		String transactionsUrl = String.format(
			ApiConstants.TRANSACTIONS_URL_FORMAT,
			accountId, fromDate, toDate
		);

		log.info("Searching transactions with URL: {}", transactionsUrl);

		WebClient client = WebClient.create(vertx);
		ObjectMapper mapper = objectMapper;

		client.requestAbs(HttpMethod.GET, transactionsUrl)
			.putHeader("Auth-Schema", authSchema)
			.putHeader("Api-Key", apiKey)
			.putHeader("Content-Type", "application/json")
			.send(ar -> {
				if (ar.succeeded()) {
					HttpResponse<Buffer> response = ar.result();
					int statusCode = response.statusCode();
					String bodyAsString = response.bodyAsString();

					log.info("Transactions API response status: {}", statusCode);

					if (statusCode >= 300) {
						log.error("Failed to retrieve transactions for validation enquiry, HTTP {}: {}", statusCode, bodyAsString);
						sendErrorResponse(message, StatusConstants.ERROR_VALIDATION_ENQUIRY_FAILED);
						return;
					}

					// Parse transactions and search for matching transfer
					try {
						TransactionDto transactionDto = mapper.readValue(bodyAsString, TransactionDto.class);
						boolean found = searchForMatchingTransfer(transactionDto, originalRequest);

						if (found) {
							log.info("Validation enquiry found matching transfer - money transfer was executed successfully");
							BonificoRestResponseDto responseDto = BonificoRestResponseDto.success(StatusConstants.STATUS_EXECUTED);
							String jsonResponse = mapper.writeValueAsString(responseDto);
							message.reply(jsonResponse);
						} else {
							log.warn("Validation enquiry did not find matching transfer - money transfer was NOT executed");
							sendErrorResponse(message, StatusConstants.ERROR_NO_MATCHING_TRANSACTION);
						}
					} catch (JsonProcessingException e) {
						log.error("Error parsing transactions response for validation enquiry", e);
						sendErrorResponse(message, StatusConstants.ERROR_VALIDATION_PARSING_FAILED);
					}
				} else {
					log.error("Failed to call transactions API for validation enquiry", ar.cause());
					sendErrorResponse(message, StatusConstants.ERROR_VALIDATION_UNAVAILABLE);
				}
			});
	}

	/**
	 * Search for a matching transfer in the transaction list.
	 * Matches by: amount, currency, and description.
	 *
	 * @param transactionDto the transactions response
	 * @param originalRequest the original money transfer request
	 * @return true if a matching transaction is found
	 */
	private boolean searchForMatchingTransfer(TransactionDto transactionDto, BonificoRestRequestDto originalRequest) {
		if (transactionDto == null || transactionDto.getPayload() == null ||
			transactionDto.getPayload().getList() == null) {
			return false;
		}

		java.math.BigDecimal expectedAmount = originalRequest.getAmount();
		String expectedCurrency = originalRequest.getCurrency();
		String expectedDescription = originalRequest.getDescription();

		log.debug("Searching for transfer: amount={}, currency={}, description={}",
			expectedAmount, expectedCurrency, expectedDescription);

		for (ListaTransactionDto transaction : transactionDto.getPayload().getList()) {
			// For outgoing transfers, amount should be negative
			boolean isOutgoing = transaction.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0;

			// Match by absolute amount (allowing for small rounding differences)
			// Compare absolute values since outgoing transfers have negative amounts
			java.math.BigDecimal actualAmountAbs = transaction.getAmount().abs();
			java.math.BigDecimal expectedAmountAbs = expectedAmount.abs();
			java.math.BigDecimal difference = actualAmountAbs.subtract(expectedAmountAbs).abs();
			boolean amountMatches = difference.compareTo(new java.math.BigDecimal("0.01")) < 0;
			boolean currencyMatches = expectedCurrency.equals(transaction.getCurrency());
			boolean descriptionMatches = expectedDescription != null &&
				expectedDescription.equals(transaction.getDescription());

			log.debug("Checking transaction {} -> amount={}, currency={}, description={}, matches={}",
				transaction.getTransactionId(), transaction.getAmount(), transaction.getCurrency(),
				transaction.getDescription(), amountMatches && currencyMatches && isOutgoing);

			if (amountMatches && currencyMatches && isOutgoing) {
				// Description match is optional as the bank may modify it
				log.info("Found matching transaction: {}", transaction);
				return true;
			}
		}

		return false;
	}

	/**
	 * Extract accountId from the money transfer URL.
	 */
	private String extractAccountIdFromUrl(String url) {
		// URL format: {FABRICK_API_BASE}/accounts/{accountId}/payments/money-transfers
		String[] parts = url.split("/accounts/");
		if (parts.length < 2) {
			return null;
		}
		String accountId = parts[1].split("/")[0];
		return accountId;
	}

	/**
	 * Send error response to the event bus message.
	 */
	private void sendErrorResponse(Message<Object> message, String errorMessage) {
		ObjectMapper mapper = objectMapper;
		BonificoRestResponseDto errorResponse = BonificoRestResponseDto.error(errorMessage);
		try {
			String errorJson = mapper.writeValueAsString(errorResponse);
			message.reply(errorJson);
		} catch (JsonProcessingException e) {
			log.error("Error serializing error response to JSON", e);
			message.fail(ErrorCode.INTERNAL_SERIALIZATION_ERROR, "ErrorCode " + ErrorCode.INTERNAL_SERIALIZATION_ERROR + " - Error serializing error response");
		}
	}

}
