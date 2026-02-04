package it.demo.fabrick;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import io.reactiverse.contextual.logging.ContextualData;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import lombok.extern.slf4j.Slf4j;

@PropertySource("${applicationPropertiesPath:file:/data/application.properties}")
@SpringBootApplication
@EnableWebSecurity
@ComponentScan(basePackages = "it.demo.fabrick")
@Slf4j
public class ContoDemoApplication {

    @Autowired
	private List<Verticle> verticleList;

	@Value("${vertx.eventLoopExecuteTime:2000000000}")
	private String eventLoopExecuteTime;

    public static void main(String[] args) {

        // lancio di SpringBoot
        SpringApplication.run(ContoDemoApplication.class, args);
    }

    @PostConstruct
	public void deployVerticle() {

		final VertxOptions vertOptions = new VertxOptions();
		vertOptions.setMaxEventLoopExecuteTime(Long.parseLong(eventLoopExecuteTime));
		Vertx vertx = Vertx.vertx(vertOptions);

        configureInterceptor(vertx);

        // i deploy sono asincroni
		verticleList.stream().forEach(verticle -> {
			vertx.deployVerticle(verticle);
		});

        // per fare in modo che quando venga chiuso SpringBoot, venga chiuso anche il contesto vert.x e tutti i verticle
        // deplotati altrimenti rimangono attivi
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
				log.info("shutdown");
                vertx.deploymentIDs().forEach(vertx::undeploy);
                vertx.close();
            }
        });

    }

    public static DeliveryOptions getDefaultDeliverOptions(){
        return new DeliveryOptions().setSendTimeout(100000); // 100 seconds for money transfer operations
    }

    private void configureInterceptor(Vertx vertx) {
        vertx.eventBus().addOutboundInterceptor(event -> {
			String requestId = ContextualData.get("requestId");
			if (requestId != null) {
				event.message().headers().add("requestId", requestId);
			}
			event.next();
		});
		
		vertx.eventBus().addInboundInterceptor(event -> {
			String requestId = event.message().headers().get("requestId");
			if (requestId != null) {
				ContextualData.put("requestId", requestId);
			}
			event.next();
		});
    }
}
