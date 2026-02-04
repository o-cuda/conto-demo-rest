package it.demo.fabrick.vertx;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import it.demo.fabrick.dto.ListaTransactionDto;
import it.demo.fabrick.dto.TransactionDto;
import it.demo.fabrick.dto.rest.TransazioniResponseDto;
import it.demo.fabrick.mapper.DtoMapper;
import it.demo.fabrick.error.ErrorCode;
import it.demo.fabrick.utils.EventBusConstants;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ListaTransazioniVerticle extends AbstractVerticle {

	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String authSchema;

	public ListaTransazioniVerticle(ObjectMapper objectMapper,
									 @Value("${fabrick.apiKey}") String apiKey,
									 @Value("${fabrick.authSchema}") String authSchema) {
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.authSchema = authSchema;
	}

	@Override
	public void start(io.vertx.core.Promise<Void> startFuture) throws Exception {

		log.info("start - lanciato");

		String bus = EventBusConstants.LISTA_BUS;
		log.debug("mi sottoscrivo al bus '{}' ..", bus);
		vertx.eventBus().consumer(bus, message -> {

			lanciaChiamataEsterna(message);
		});
	}

	public void lanciaChiamataEsterna(Message<Object> message) {

		log.info("lanciaChiamataEsterna - start");

		JsonObject json = (JsonObject) message.body();

		String indirizzo = json.getString("indirizzo");
		String requestId = json.getString("requestId");

		log.info("message.body().\"indirizzo\" = {}", indirizzo);
		log.info("message.body().\"requestId\" = {}", requestId);

		WebClient client = WebClient.create(vertx);
		ObjectMapper mapper = objectMapper;

		log.debug("richiamo servizio REST ...");
		client.requestAbs(HttpMethod.GET, indirizzo)
				.putHeader("Content-Type", "application/json")
				.putHeader("Auth-Schema", authSchema)
				.putHeader("Api-Key", apiKey)
				.putHeader("Content-Type", "application/json")
				.sendBuffer(Buffer.buffer(""), ar -> {
					if (ar.succeeded()) {

						HttpResponse<Buffer> response = ar.result();
						int statusCode = response.statusCode();
						@Nullable
						String bodyAsString = response.bodyAsString();

						log.info("Received response with status code: {}", statusCode);

						if (statusCode >= 300) {
							// Try to parse ErrorDto from Fabrick API response
							it.demo.fabrick.dto.ErrorDto errorDto = null;
							try {
								errorDto = mapper.readValue(bodyAsString, it.demo.fabrick.dto.ErrorDto.class);
							} catch (JsonProcessingException e) {
								log.error("Error parsing error response from Fabrick API", e);
							}

							String errorMessage;
							if (errorDto != null && errorDto.getErrors() != null && !errorDto.getErrors().isEmpty()) {
								// Format error details from ErrorDto
								StringBuilder builder = new StringBuilder("ErrorCode " + ErrorCode.API_ERROR + " - ");
								errorDto.getErrors().forEach(anError -> {
									builder.append("code: ").append(anError.getCode())
										.append(", description: ").append(anError.getDescription()).append("; ");
								});
								errorMessage = builder.toString();
								log.error("Fabrick API error: {}", errorMessage);
							} else {
								errorMessage = "ErrorCode " + ErrorCode.API_ERROR + " - API returned HTTP " + statusCode + ": " + bodyAsString;
								log.error(errorMessage);
							}
							message.fail(ErrorCode.API_ERROR, errorMessage);
							return;
						}

						log.info("bodyAsString: {}", bodyAsString);

						TransactionDto transaction = null;
						String listaTransazioni = null;
						try {
							transaction = mapper.readValue(bodyAsString, TransactionDto.class);

							// Convert to REST response DTO using DtoMapper
							TransazioniResponseDto responseDto = DtoMapper.toTransazioniResponseDto(transaction);
							listaTransazioni = mapper.writeValueAsString(responseDto);
						} catch (JsonProcessingException e) {
							log.error("Error parsing JSON response from Fabrick API", e);
							message.fail(ErrorCode.API_PARSE_ERROR, "ErrorCode " + ErrorCode.API_PARSE_ERROR + " - Error parsing JSON response");
							return;
						}

						message.reply(listaTransazioni);

						// After replying to the REST API, trigger async database write
						// This is fire-and-forget - the client has already received the response
						triggerAsyncPersistence(transaction, requestId);

					} else {
						String errorMessage = String.format("ErrorCode %d - Unable to call Fabrick API, service may be down: %s",
							ErrorCode.API_CONNECTION_FAILED, ar.cause().getMessage());
						log.error(errorMessage, ar.cause());
						message.fail(ErrorCode.API_CONNECTION_FAILED, errorMessage);
					}
				});
	}

	/**
	 * Trigger asynchronous persistence of transactions to the database.
	 * This method sends the transactions to the persistence verticle via event bus.
	 *
	 * @param transaction the transaction DTO containing the list of transactions
	 * @param requestId the request ID for logging
	 */
	private void triggerAsyncPersistence(TransactionDto transaction, String requestId) {
		try {
			// Check if there are transactions to persist
			if (transaction == null || transaction.getPayload() == null ||
				transaction.getPayload().getList() == null ||
				transaction.getPayload().getList().isEmpty()) {
				log.debug("No transactions to persist for requestId: {}", requestId);
				return;
			}

			log.info("Triggering async persistence of {} transactions for requestId: {}",
				transaction.getPayload().getList().size(), requestId);

			// Convert the transaction list to JsonArray for event bus transport
			// We need to convert DTOs to Map/JsonObject because Vert.x JSON codec
			// cannot serialize arbitrary Java objects
			ObjectMapper mapper = objectMapper;
			JsonArray transactionsArray = new JsonArray();

			for (it.demo.fabrick.dto.ListaTransactionDto dto : transaction.getPayload().getList()) {
				// Convert DTO to Map using Jackson, then to JsonObject
				@SuppressWarnings("unchecked")
				java.util.Map<String, Object> map = mapper.convertValue(dto, java.util.Map.class);
				transactionsArray.add(new JsonObject(map));
			}

			JsonObject persistenceMessage = new JsonObject()
				.put("requestId", requestId)
				.put("transactions", transactionsArray);

			// Send to persistence verticle (fire-and-forget)
			vertx.eventBus().send(EventBusConstants.TRANSACTION_PERSISTENCE_BUS, persistenceMessage);

			log.debug("Async persistence triggered for requestId: {}", requestId);

		} catch (Exception e) {
			// Log error but don't fail - the REST response has already been sent
			log.error("Error triggering async persistence for requestId: {}", requestId, e);
		}
	}

}
