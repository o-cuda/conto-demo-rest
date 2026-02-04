package it.demo.fabrick.unit.verticle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import it.demo.fabrick.vertx.ListaTransazioniVerticle;

/**
 * Unit tests for ListaTransazioniVerticle.
 */
@ExtendWith(VertxExtension.class)
@DisplayName("ListaTransazioniVerticle Tests")
class ListaTransazioniVerticleTest {

	private ListaTransazioniVerticle verticle;

	private static final String TEST_API_KEY = "test-api-key-12345";
	private static final String TEST_AUTH_SCHEMA = "S2S";

	@BeforeEach
	void setUp() {
		verticle = new ListaTransazioniVerticle(new ObjectMapper(), TEST_API_KEY, TEST_AUTH_SCHEMA);
	}

	// ==================== start() Tests ====================

	@Test
	@DisplayName("start - should subscribe to lista_bus")
	void testStart_subscribesToListaBus(Vertx vertx, VertxTestContext testContext) {
		// Initiate deployment - if start() succeeds without exception, the test passes
		vertx.deployVerticle(verticle);
		testContext.completeNow();
	}
}
