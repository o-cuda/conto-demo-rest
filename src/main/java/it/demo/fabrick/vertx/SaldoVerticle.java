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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import it.demo.fabrick.dto.BalanceDto;
import it.demo.fabrick.dto.rest.SaldoResponseDto;
import it.demo.fabrick.error.ErrorCode;
import it.demo.fabrick.utils.EventBusConstants;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SaldoVerticle extends AbstractVerticle {

	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String authSchema;

	public SaldoVerticle(ObjectMapper objectMapper,
						 @Value("${fabrick.apiKey}") String apiKey,
						 @Value("${fabrick.authSchema}") String authSchema) {
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.authSchema = authSchema;
	}

	@Override
	public void start(io.vertx.core.Promise<Void> startFuture) throws Exception {

		log.info("start - lanciato");

		String bus = EventBusConstants.SALDO_BUS;
		log.debug("mi sottoscrivo al bus '{}' ..", bus);
		vertx.eventBus().consumer(bus, message -> {

			lanciaChiamataEsterna(message);
		});
	}

	public void lanciaChiamataEsterna(Message<Object> message) {

		log.info("lanciaChiamataEsterna - start");

		JsonObject json = (JsonObject) message.body();

		String indirizzo = json.getString("indirizzo");

		log.info("message.body().\"indirizzo\" = {}", indirizzo);

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

						BalanceDto balance = null;
						try {
							balance = mapper.readValue(bodyAsString, BalanceDto.class);
						} catch (JsonProcessingException e) {
							log.error("Error parsing JSON response from Fabrick API", e);
							message.fail(ErrorCode.API_PARSE_ERROR, "ErrorCode " + ErrorCode.API_PARSE_ERROR + " - Error parsing JSON response");
							return;
						}

						// Convert to REST response DTO
						SaldoResponseDto responseDto = SaldoResponseDto.fromBalanceDto(balance.getPayload());
						String jsonResponse;
						try {
							jsonResponse = mapper.writeValueAsString(responseDto);
						} catch (JsonProcessingException e) {
							log.error("Error serializing response to JSON", e);
							message.fail(ErrorCode.INTERNAL_SERIALIZATION_ERROR, "ErrorCode " + ErrorCode.INTERNAL_SERIALIZATION_ERROR + " - Error serializing response");
							return;
						}

						message.reply(jsonResponse);

					} else {
						String errorMessage = String.format("ErrorCode %d - Unable to call Fabrick API, service may be down: %s",
							ErrorCode.API_CONNECTION_FAILED, ar.cause().getMessage());
						log.error(errorMessage, ar.cause());
						message.fail(ErrorCode.API_CONNECTION_FAILED, errorMessage);
					}
				});
	}

}
