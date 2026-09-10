package dev.manuel.gymtracker_api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void shouldConfigureGymTrackerJwtBearerDocumentation() {
        var openApi = new OpenApiConfig().gymTrackerOpenApi();

        assertEquals("Gym Tracker API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
        assertNotNull(openApi.getComponents().getSecuritySchemes().get("bearerAuth"));
        assertEquals("bearer", openApi.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
    }
}
