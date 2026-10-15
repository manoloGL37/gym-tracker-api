package dev.manuel.gymtracker_api.workout.controller;

import dev.manuel.gymtracker_api.config.GlobalExceptionHandler;
import dev.manuel.gymtracker_api.workout.service.WorkoutService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LegacyWorkoutValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"80.000", "82.500", "0.000", "9999.990"})
    void android101TrailingZerosAreValidKilograms(String weight) throws Exception {
        var service = mock(WorkoutService.class);
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var mvc = MockMvcBuilders.standaloneSetup(new WorkoutController(service))
                    .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
            mvc.perform(post("/api/workouts/mobile").contentType(MediaType.APPLICATION_JSON).content("""
                {"clientId":"00000000-0000-4000-8000-000000000001","nameSnapshot":"Tirón",
                 "startedAt":"2026-10-13T08:00:00Z","completedAt":"2026-10-13T09:00:00Z",
                 "calendarZone":"Europe/Madrid","exercises":[{
                   "clientId":"00000000-0000-4000-8000-000000000002",
                   "exerciseNameSnapshot":"Remo","position":0,"sets":[{
                     "clientId":"00000000-0000-4000-8000-000000000003",
                     "setNumber":1,"weight":%s,"reps":10}]}]}
                """.formatted(weight))).andExpect(status().isCreated());
            verify(service).createMobileWorkout(any(), any());
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"1.001", "-0.01", "10000", "9999.991"})
    void realPrecisionAndRangeErrorsRemainRejected(String weight) throws Exception {
        var service = mock(WorkoutService.class);
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var mvc = MockMvcBuilders.standaloneSetup(new WorkoutController(service))
                    .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
            mvc.perform(post("/api/workouts/00000000-0000-4000-8000-000000000001/exercises/00000000-0000-4000-8000-000000000002/sets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"setNumber\":1,\"reps\":10,\"weight\":" + weight + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            verifyNoInteractions(service);
        }
    }

}
