package dev.manuel.gymtracker_api.config;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import dev.manuel.gymtracker_api.auth.exception.InvalidCredentialsException;
import dev.manuel.gymtracker_api.auth.exception.InvalidRefreshTokenException;
import dev.manuel.gymtracker_api.common.exception.ResourceNotFoundException;
import dev.manuel.gymtracker_api.routine.exception.DuplicateRoutineExercisePositionException;
import dev.manuel.gymtracker_api.user.exception.EmailAlreadyExistsException;
import dev.manuel.gymtracker_api.workout.exception.DuplicateWorkoutSetNumberException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(EmailAlreadyExistsException.class)
        @ResponseStatus(HttpStatus.CONFLICT)
        public ErrorResponse handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
                return new ErrorResponse(
                                HttpStatus.CONFLICT.value(),
                                "EMAIL_ALREADY_EXISTS",
                                exception.getMessage(),
                                LocalDateTime.now());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public ValidationErrorResponse handleValidation(
                        MethodArgumentNotValidException exception) {
                Map<String, String> errors = exception.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .collect(Collectors.toMap(
                                                error -> error.getField(),
                                                error -> error.getDefaultMessage(),
                                                (first, second) -> first));

                return new ValidationErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "VALIDATION_ERROR",
                                errors,
                                LocalDateTime.now());
        }

        @ExceptionHandler(ConstraintViolationException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public ValidationErrorResponse handleConstraintViolation(
                        ConstraintViolationException exception) {
                Map<String, String> errors = exception.getConstraintViolations()
                                .stream()
                                .collect(Collectors.toMap(
                                                violation -> violation.getPropertyPath().toString(),
                                                violation -> violation.getMessage(),
                                                (first, second) -> first));

                return new ValidationErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "VALIDATION_ERROR",
                                errors,
                                LocalDateTime.now());
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        public ErrorResponse handleResourceNotFound(ResourceNotFoundException exception) {
                return new ErrorResponse(
                                HttpStatus.NOT_FOUND.value(),
                                "RESOURCE_NOT_FOUND",
                                exception.getMessage(),
                                LocalDateTime.now());
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public ErrorResponse handleMethodArgumentTypeMismatch(
                        MethodArgumentTypeMismatchException exception) {
                return new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "INVALID_PARAMETER",
                                "Invalid parameter: " + exception.getName(),
                                LocalDateTime.now());
        }

        @Schema(name = "ApiError", description = "Error returned for domain and request failures.")
        public record ErrorResponse(
                        int status,
                        String code,
                        String message,
                        LocalDateTime timestamp) {
        }

        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleInvalidCredentials(
                        InvalidCredentialsException exception) {
                ErrorResponse response = new ErrorResponse(
                                HttpStatus.UNAUTHORIZED.value(),
                                "INVALID_CREDENTIALS",
                                exception.getMessage(),
                                LocalDateTime.now());

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(response);
        }

        @ExceptionHandler(InvalidRefreshTokenException.class)
        @ResponseStatus(HttpStatus.UNAUTHORIZED)
        public ErrorResponse handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
                return new ErrorResponse(
                                HttpStatus.UNAUTHORIZED.value(),
                                "INVALID_REFRESH_TOKEN",
                                exception.getMessage(),
                                LocalDateTime.now());
        }

        @ExceptionHandler(HandlerMethodValidationException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public ValidationErrorResponse handleMethodValidation(
                        HandlerMethodValidationException exception) {
                Map<String, String> errors = Map.of(
                                "parameters",
                                "One or more parameters have invalid values");

                return new ValidationErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "VALIDATION_ERROR",
                                errors,
                                LocalDateTime.now());
        }

        @ExceptionHandler(DuplicateRoutineExercisePositionException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public ErrorResponse handleDuplicateRoutineExercisePosition(
                        DuplicateRoutineExercisePositionException exception) {
                return new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "DUPLICATE_EXERCISE_POSITION",
                                exception.getMessage(),
                                LocalDateTime.now());
        }

        @ExceptionHandler(DuplicateWorkoutSetNumberException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public ErrorResponse handleDuplicateWorkoutSetNumber(
                DuplicateWorkoutSetNumberException exception) {

        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "DUPLICATE_WORKOUT_SET_NUMBER",
                exception.getMessage(),
                LocalDateTime.now());
        }

        @ExceptionHandler(IllegalArgumentException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public ErrorResponse handleIllegalArgumentException(
                IllegalArgumentException exception
        ) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                exception.getMessage(),
                LocalDateTime.now()
        );
        }

        @Schema(name = "ValidationError", description = "Validation error keyed by field or parameter name.")
        public record ValidationErrorResponse(
                        int status,
                        String code,
                        Map<String, String> errors,
                        LocalDateTime timestamp) {
        }
}
