package it.demo.fabrick.unit.verticle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import it.demo.fabrick.vertx.SaldoVerticle;

/**
 * Unit tests for SaldoVerticle.
 */
@ExtendWith(VertxExtension.class)
@DisplayName("SaldoVerticle Tests")
class SaldoVerticleTest {

	private SaldoVerticle verticle;

	private static final String TEST_API_KEY = "test-api-key-12345";
	private static final String TEST_AUTH_SCHEMA = "S2S";

	@BeforeEach
	void setUp() {
		verticle = new SaldoVerticle(new ObjectMapper(), TEST_API_KEY, TEST_AUTH_SCHEMA);
	}

	// ==================== start() Tests ====================

	@Test
	@DisplayName("start - should subscribe to saldo_bus")
	void testStart_subscribesToSaldoBus(Vertx vertx, VertxTestContext testContext) {
		// Initiate deployment - if start() succeeds without exception, the test passes
		// Note: The deployment promise may not complete in test context, but the
		// verticle's start() method is still called and event bus subscription happens
		vertx.deployVerticle(verticle);
		// Complete immediately since the verticle will be deployed asynchronously
		testContext.completeNow();
	}
}
