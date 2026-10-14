package dev.manuel.gymtracker_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class TrackingJsonConfig {
    @Bean
    JsonMapperBuilderCustomizer exactIntegers() {
        return builder -> builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
