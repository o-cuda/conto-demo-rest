package it.demo.fabrick.vertx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

// @Component - Removed in REST API conversion, configuration now hardcoded
public class ConfigVerticle extends AbstractVerticle {

	private static Logger log = LoggerFactory.getLogger(ConfigVerticle.class);

	@Value("${amb.lavoro:SVIL}")
	private String ambiente;

	@Value("${spring.datasource.username}")
	private String username;

	@Value("${spring.datasource.password}")
	private String pass;

	private JDBCClient client = null;

	//@f:off
	private static final String QUERY = " select "
						+ "      con.OPERATION, " 
						+ "      ind.AMBIENTE, "  
						+ "      con.MESSAGE_IN, "  
						+ "      con.MESSAGE_OUT_BUS," 
						+ "      ind.INDIRIZZO, " 
						+ " from CONTO_CONFIGURATION con" 
						+ " inner join CONTO_INDIRIZZI ind on (con.OPERATION = ind.OPERATION)"
						+ " where ind.OPERATION = ? and ind.AMBIENTE = ? ";
	//@f:on

	@Override
	public void start(io.vertx.core.Promise<Void> startFuture) throws Exception {

		log.debug("variabile 'ambiente' in input: {}", ambiente);

		log.info("start - lanciato");
		log.debug("creazione JDBCClient .. ");

		client = JDBCClient.createShared(vertx,
				new JsonObject().put("url", "jdbc:h2:mem:db1").put("driver_class", "org.h2.Driver").put("max_pool_size", 30).put("user", username)
						.put("password", pass));

		log.debug("mi sottoscrivo al bus 'get-configurazione-bus' ..");
		vertx.eventBus().consumer("get-configurazione-bus", message -> {

			leggiConfigurazione(message);
		});
	}

	public void leggiConfigurazione(Message<Object> message) {
		
		log.info("leggiConfigurazione - start");

		Object body = message.body();
		log.debug("1 received message.body() = " + body);

		String operazioneInEntrata = body.toString();

		log.trace("getConnection ..");
		client.getConnection(conn -> {

			if (conn.failed()) {
				log.error(conn.cause().getMessage());
				message.fail(1, String.format("%s", conn.cause().getMessage()));
				return;
			}

			final SQLConnection connection = conn.result();

			log.trace("queryWithParams: operazione[{}], ambiente[{}]", operazioneInEntrata, ambiente);
			connection.queryWithParams(QUERY, new JsonArray().add(operazioneInEntrata).add(ambiente), rs -> {

				if (rs.failed()) {
					String errorMessage = "Cannot retrieve the data from the database";
					log.error(errorMessage, rs.cause());
					message.fail(1, String.format("%s %s", errorMessage, rs.cause().getMessage()));
					return;
				}

				if (rs.result().getResults().isEmpty()) {
					String errorMessage = "nessuna configurazione trovata per l'operazione " + operazioneInEntrata;
					log.error(errorMessage);
					message.fail(1, String.format("%s", errorMessage));
					return;
				} else if (rs.result().getResults().size() > 1) {

					String errorMessage = "trovata piu' di una configurazione per l'operazione " + operazioneInEntrata;
					log.error(errorMessage);
					message.fail(1, String.format("%s", errorMessage));
					return;
				}

				for (JsonArray line : rs.result().getResults()) {

					log.trace("line: {}", line);
					// butto il JSON direttamente come risposta del messaggio in coda
					// potrei anche buttare un oggetto, ma bisogna creare un mapper, invece così non serve
					message.reply(line);
				}

				// and close the connection
				connection.close(done -> {

					if (done.failed()) {
						log.error("connection close KO");
						throw new RuntimeException(done.cause());
					} else {
						log.trace("connection close OK");
					}
				});

			});
		});
	}

}