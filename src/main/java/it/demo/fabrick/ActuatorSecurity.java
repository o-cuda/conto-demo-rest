package it.demo.fabrick;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class ActuatorSecurity {

	@Bean
	public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(EndpointRequest.toAnyEndpoint())
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

		return http.build();
	}

}
