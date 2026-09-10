package dev.manuel.gymtracker_api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.manuel.gymtracker_api.statistics.controller.StatisticsController;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

class OpenApiConfigTest {

    @Test
    void shouldConfigureGymTrackerJwtBearerDocumentation() {
        var openApi = new OpenApiConfig().gymTrackerOpenApi();

        assertEquals("Gym Tracker API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
        assertNotNull(openApi.getComponents().getSecuritySchemes().get("bearerAuth"));
        assertEquals("bearer", openApi.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
    }

    @Test
    void shouldNotDocumentExerciseStatisticsAsNotFound() throws NoSuchMethodException {
        ApiResponses responses = StatisticsController.class
                .getMethod("getExerciseStatistics", UUID.class, UUID.class, LocalDate.class, LocalDate.class)
                .getAnnotation(ApiResponses.class);

        assertNotNull(responses);
        assertEquals(0, java.util.Arrays.stream(responses.value())
                .filter(response -> response.responseCode().equals("404"))
                .count());
    }
}
