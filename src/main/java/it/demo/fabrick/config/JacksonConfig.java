package it.demo.fabrick.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Jackson ObjectMapper.
 * Separated from ContoDemoApplication to avoid circular dependency.
 */
@Configuration
public class JacksonConfig {

    /**
     * ObjectMapper bean for JSON serialization/deserialization.
     * Shared across all verticles instead of creating new instances.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
