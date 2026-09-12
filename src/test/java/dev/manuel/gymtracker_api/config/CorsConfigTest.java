package dev.manuel.gymtracker_api.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    void shouldRejectWildcardOriginsForCredentialedCors() {
        assertThrows(IllegalArgumentException.class, () -> corsConfig.corsConfigurationSource("*"));
        assertThrows(IllegalArgumentException.class,
                () -> corsConfig.corsConfigurationSource("https://gym-tracker-eight-dun.vercel.app,*"));
    }

    @Test
    void shouldConfigureExactOriginsWithCredentials() {
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource)
                corsConfig.corsConfigurationSource("https://gym-tracker-eight-dun.vercel.app");
        var configuration = source.getCorsConfigurations().get("/**");

        assertEquals(true, configuration.getAllowCredentials());
        assertEquals(
                java.util.List.of("https://gym-tracker-eight-dun.vercel.app"),
                configuration.getAllowedOrigins()
        );
    }
}
