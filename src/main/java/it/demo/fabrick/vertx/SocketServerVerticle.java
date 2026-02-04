package it.demo.fabrick.vertx;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.reactiverse.contextual.logging.ContextualData;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import it.demo.fabrick.ContoDemoApplication;

// @Component - Removed in REST API conversion, replaced by HttpServerVerticle
public class SocketServerVerticle extends AbstractVerticle {

	private Logger log = LoggerFactory.getLogger(getClass());

	private static final String CHARSET = "cp280";
//	private static final String charset = "UTF-8";

	@Override
	public void start() throws Exception {
		
		
		
		NetServerOptions options = new NetServerOptions().setPort(9221);
		options.setIdleTimeout(55);
		NetServer server = vertx.createNetServer(options);

		server.connectHandler(socket -> {

			// Ricezione dei dati da CICS
			socket.handler(bufferIn -> {
				String requestId = UUID.randomUUID().toString();
				ContextualData.put("requestId", requestId);
				String messageInMessage = bufferIn.toString(CHARSET);
				log.info("messageInMessage: [{}]", messageInMessage);

				vertx.eventBus().request("gestisci-chiamata-bus", messageInMessage,
						ContoDemoApplication.getDefaultDeliverOptions(), asyncResult -> {

					if (asyncResult.succeeded()) {

								String messageOut = (String) asyncResult.result().body();
								log.info("messageOut: {}", messageOut);

						int stringLen = 500;

						// String stringCallBack = conv;
								String stringOut = "0" + messageOut;
						String stringFill = String.format("%-" + (stringLen) + "s", stringOut);

						socket.write(stringFill, CHARSET);
						log.info("OUT: [{}] \n LEN: [{}]", stringFill, stringFill.length());
					} else {
						String errore = String.format("1[%s] %s", requestId, asyncResult.cause().getMessage());
						log.error("OUT: [{}]", errore);
						socket.write(errore, CHARSET);
					}
					socket.end();
				});

			});

			log.debug("Apertura socket in corso");

			// Intercettazione dell'evento di chiusura del socket
			socket.closeHandler(v -> log.debug("Il socket è stato chiuso") );

		});

		server.listen(res -> {
			if (res.succeeded()) {
				log.info("Il {} e' in ascolto!", getClass().getName());
			} else {
				log.error("Errore nell'avvio del {}!", getClass().getName());
			}
		});
	}

}
