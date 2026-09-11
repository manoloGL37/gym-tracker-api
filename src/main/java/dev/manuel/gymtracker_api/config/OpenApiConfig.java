package dev.manuel.gymtracker_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gymTrackerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gym Tracker API")
                        .description("REST API for managing users, exercises, routines, workouts, and training statistics.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a JWT access token obtained from login or refresh."))
                        .addSecuritySchemes("refreshCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("refreshToken")
                                .description("HttpOnly refresh cookie set and rotated by the server.")));
    }
}
