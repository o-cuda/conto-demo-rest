package it.demo.fabrick.unit.testutil;

import org.mockito.Mockito;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;

/**
 * Shared test utilities for verticle unit testing.
 * Provides common mocking helpers to reduce test code duplication.
 */
public class VerticleTestUtils {

	/**
	 * Create a mock Message for testing.
	 *
	 * @param body the message body content
	 * @return mocked Message object
	 */
	@SuppressWarnings("unchecked")
	public static <T> Message<T> mockMessage(T body) {
		Message<T> mockMessage = Mockito.mock(Message.class);
		Mockito.when(mockMessage.body()).thenReturn(body);
		return mockMessage;
	}

	/**
	 * Create a mock event bus for testing.
	 * The mock event bus can be configured to return specific responses.
	 *
	 * @return mocked EventBus object
	 */
	public static EventBus mockEventBus() {
		return Mockito.mock(EventBus.class);
	}

	/**
	 * Create a mock Vertx instance with a pre-configured event bus.
	 *
	 * @return mocked Vertx object
	 */
	public static Vertx mockVertx() {
		Vertx mockVertx = Mockito.mock(Vertx.class);
		EventBus mockEventBus = mockEventBus();
		Mockito.when(mockVertx.eventBus()).thenReturn(mockEventBus);
		return mockVertx;
	}

	/**
	 * Create a standard configuration response for testing GestisciRequestVerticle.
	 *
	 * @param operation the operation code (LIS, BON, SAL)
	 * @param bus the target event bus address
	 * @param url the API endpoint URL
	 * @return JsonArray containing configuration
	 */
	public static JsonArray createConfigResponse(String operation, String bus, String url) {
		return new JsonArray()
			.add(operation)
			.add("prod")
			.add("operazione=3;accountNumber=10")
			.add(bus)
			.add(url);
	}

	/**
	 * Create a standard configuration response with custom message format.
	 *
	 * @param operation the operation code
	 * @param ambiente the environment
	 * @param messageIn the input format configuration
	 * @param bus the target event bus address
	 * @param url the API endpoint URL
	 * @return JsonArray containing configuration
	 */
	public static JsonArray createConfigResponse(String operation, String ambiente, String messageIn, String bus, String url) {
		return new JsonArray()
			.add(operation)
			.add(ambiente)
			.add(messageIn)
			.add(bus)
			.add(url);
	}

	/**
	 * Create a standard JSON request body for HTTP verticle tests.
	 *
	 * @param url the API endpoint URL
	 * @return JsonObject with indirizzo field
	 */
	public static io.vertx.core.json.JsonObject createHttpRequestBody(String url) {
		return new io.vertx.core.json.JsonObject().put("indirizzo", url);
	}

	private VerticleTestUtils() {
		// Utility class - prevent instantiation
	}
}
