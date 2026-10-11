package es.bluesolution.railway_framework.framework.functional_framework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test for HttpStatusMapper ensuring all error codes and exceptions
 * are properly mapped to appropriate HTTP status codes.
 */
@DisplayName("HttpStatusMapper - Comprehensive Error Code Coverage")
class HttpStatusMapperTest {

    @ParameterizedTest
    @EnumSource(FailureResultDescription.ErrorCode.class)
    @DisplayName("All error codes should map to valid HTTP status")
    void should_map_all_error_codes_to_valid_http_status(FailureResultDescription.ErrorCode errorCode) {
        // Given
        FailureResultDescription failure = new FailureResultDescription(errorCode, "Test message");

        // When
        HttpStatus status = HttpStatusMapper.mapToHttpStatus(failure);

        // Then
        assertThat(status).isNotNull();
        assertThat(status.value()).isBetween(400, 599); // Should be a client or server error

        // Verify semantic correctness
        switch (errorCode) {
            case VALIDATION_ERROR -> assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
            case AUTHENTICATION_ERROR -> assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
            case AUTHORIZATION_ERROR -> assertThat(status).isEqualTo(HttpStatus.FORBIDDEN);
            case NOT_FOUND -> assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
            case BUSINESS_RULE_ERROR -> assertThat(status).isEqualTo(HttpStatus.CONFLICT);
            case RATE_LIMIT_ERROR -> assertThat(status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            case TECHNICAL_ERROR -> assertThat(status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            case DATABASE_ERROR -> assertThat(status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            case CONFIGURATION_ERROR -> assertThat(status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            case EXTERNAL_SERVICE_ERROR -> assertThat(status).isEqualTo(HttpStatus.BAD_GATEWAY);
            case SERVICE_UNAVAILABLE_ERROR -> assertThat(status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            case TIMEOUT_ERROR -> assertThat(status).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            case UNKNOWN_ERROR -> assertThat(status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @DisplayName("Common JDK exceptions should map correctly")
    void should_map_jdk_exceptions_correctly() {
        // Client errors (4xx)
        assertExceptionMapsToStatus(new IllegalArgumentException("Invalid input"), HttpStatus.BAD_REQUEST);
        assertExceptionMapsToStatus(new NumberFormatException("Invalid number"), HttpStatus.BAD_REQUEST);
        // ClassCastException is a runtime error, maps to INTERNAL_SERVER_ERROR
        assertExceptionMapsToStatus(new ClassCastException("Invalid cast"), HttpStatus.INTERNAL_SERVER_ERROR);

        // Conflict errors
        assertExceptionMapsToStatus(new IllegalStateException("Invalid state"), HttpStatus.CONFLICT);

        // Not found errors
        assertExceptionMapsToStatus(new java.util.NoSuchElementException("Element not found"), HttpStatus.NOT_FOUND);
        assertExceptionMapsToStatus(new java.io.FileNotFoundException("File not found"), HttpStatus.NOT_FOUND);

        // Forbidden errors
        assertExceptionMapsToStatus(new SecurityException("Access denied"), HttpStatus.FORBIDDEN);

        // Service availability errors
        assertExceptionMapsToStatus(new java.net.ConnectException("Connection failed"), HttpStatus.SERVICE_UNAVAILABLE);
        assertExceptionMapsToStatus(new java.net.SocketTimeoutException("Socket timeout"), HttpStatus.GATEWAY_TIMEOUT);
        assertExceptionMapsToStatus(new TimeoutException("Operation timeout"), HttpStatus.REQUEST_TIMEOUT);

        // Rate limiting / Executor rejected (maps to runtime error)
        assertExceptionMapsToStatus(new RejectedExecutionException("Executor rejected"), HttpStatus.INTERNAL_SERVER_ERROR);

        // Server errors
        assertExceptionMapsToStatus(new NullPointerException("Null pointer"), HttpStatus.INTERNAL_SERVER_ERROR);

        // Note: OutOfMemoryError and StackOverflowError are Error types, not Exception types
        // They are handled separately in the exception mapping but don't extend Exception
    }

    @Test
    @DisplayName("Spring framework exceptions should map correctly")
    void should_map_spring_exceptions_correctly() {
        // Validation errors - need to create a proper BindingResult
        org.springframework.validation.BeanPropertyBindingResult bindingResult =
            new org.springframework.validation.BeanPropertyBindingResult(new Object(), "test");
        bindingResult.reject("validation.error", "Validation failed");

        assertExceptionMapsToStatus(
            new org.springframework.web.bind.MethodArgumentNotValidException(
                null, bindingResult),  // Pass the proper BindingResult
            HttpStatus.valueOf(422) // Unprocessable Entity
        );

        assertExceptionMapsToStatus(
            new org.springframework.web.bind.MissingServletRequestParameterException("param", "String"),
            HttpStatus.BAD_REQUEST
        );

        // Security errors
        assertExceptionMapsToStatus(
            new org.springframework.security.authentication.BadCredentialsException("Bad credentials"),
            HttpStatus.UNAUTHORIZED
        );

        assertExceptionMapsToStatus(
            new org.springframework.security.access.AccessDeniedException("Access denied"),
            HttpStatus.FORBIDDEN
        );

        // Not found errors
        assertExceptionMapsToStatus(
            new org.springframework.web.servlet.NoHandlerFoundException("GET", "/unknown", null),
            HttpStatus.NOT_FOUND
        );

        // Method not allowed
        assertExceptionMapsToStatus(
            new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"),
            HttpStatus.METHOD_NOT_ALLOWED
        );
    }

    @Test
    @DisplayName("JPA/Hibernate exceptions should map correctly")
    void should_map_jpa_exceptions_correctly() {
        // Not found
        assertExceptionMapsToStatus(
            new jakarta.persistence.EntityNotFoundException("Entity not found"),
            HttpStatus.NOT_FOUND
        );

        assertExceptionMapsToStatus(
            new org.springframework.dao.EmptyResultDataAccessException(1),
            HttpStatus.NOT_FOUND
        );

        // Conflict
        assertExceptionMapsToStatus(
            new org.springframework.dao.DataIntegrityViolationException("Constraint violation"),
            HttpStatus.CONFLICT
        );

        assertExceptionMapsToStatus(
            new org.springframework.dao.DuplicateKeyException("Duplicate key"),
            HttpStatus.CONFLICT
        );

        // DataAccessResourceFailureException is a generic data access error, maps to INTERNAL_SERVER_ERROR
        assertExceptionMapsToStatus(
            new org.springframework.dao.DataAccessResourceFailureException("Database unavailable"),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @Test
    @DisplayName("Exception hierarchy traversal should work correctly")
    void should_traverse_exception_hierarchy_correctly() {
        // Create a custom exception that extends a mapped exception
        class CustomIllegalArgumentException extends IllegalArgumentException {
            public CustomIllegalArgumentException(String message) {
                super(message);
            }
        }

        // Should map to BAD_REQUEST due to inheritance from IllegalArgumentException
        assertExceptionMapsToStatus(
            new CustomIllegalArgumentException("Custom validation error"),
            HttpStatus.BAD_REQUEST
        );
    }

    @Test
    @DisplayName("Exception mapping should take precedence over error code mapping")
    void should_use_exception_mapping_over_error_code() {
        // Create a failure with an unmapped exception and validation error code
        class UnmappedException extends RuntimeException {
            public UnmappedException(String message) {
                super(message);
            }
        }

        FailureResultDescription failure = new FailureResultDescription(
            FailureResultDescription.ErrorCode.VALIDATION_ERROR,  // Would normally map to BAD_REQUEST
            "Test message",
            new UnmappedException("Unknown exception")  // But this inherits from RuntimeException
        );

        // When
        HttpStatus status = HttpStatusMapper.mapToHttpStatus(failure);

        // Then - Should use exception mapping (RuntimeException -> INTERNAL_SERVER_ERROR)
        // not error code mapping (VALIDATION_ERROR -> BAD_REQUEST)
        assertThat(status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Error code mapping should be used when no exception mapping exists")
    void should_use_error_code_when_no_exception_mapping() {
        // Create a failure with no exception, only error code
        FailureResultDescription failure = new FailureResultDescription(
            FailureResultDescription.ErrorCode.VALIDATION_ERROR,
            "Test message"
            // No exception provided
        );

        // When
        HttpStatus status = HttpStatusMapper.mapToHttpStatus(failure);

        // Then - Should use error code mapping since there's no exception
        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Future-proof design - adding new error code should require switch update")
    void should_demonstrate_future_proof_design() {
        // This test documents the design principle:
        // If a new ErrorCode is added to the enum, the switch expression
        // in HttpStatusMapper.mapErrorCodeToStatus() will cause a compilation
        // error, forcing developers to handle the new error code explicitly.

        // This prevents the common issue of adding error codes but forgetting
        // to update the mapping logic.

        // The test itself just verifies we have comprehensive coverage
        FailureResultDescription.ErrorCode[] allErrorCodes = FailureResultDescription.ErrorCode.values();
        assertThat(allErrorCodes).hasSize(13); // Update this when adding new error codes

        // Verify each error code maps successfully (would fail if switch is incomplete)
        for (FailureResultDescription.ErrorCode errorCode : allErrorCodes) {
            FailureResultDescription failure = new FailureResultDescription(errorCode, "Test");
            HttpStatus status = HttpStatusMapper.mapToHttpStatus(failure);
            assertThat(status).isNotNull();
        }
    }

    private void assertExceptionMapsToStatus(Exception exception, HttpStatus expectedStatus) {
        FailureResultDescription failure = new FailureResultDescription(
            FailureResultDescription.ErrorCode.UNKNOWN_ERROR,
            "Test message",
            exception
        );

        HttpStatus actualStatus = HttpStatusMapper.mapToHttpStatus(failure);

        assertThat(actualStatus)
            .withFailMessage("Exception %s should map to %s but mapped to %s",
                exception.getClass().getSimpleName(), expectedStatus, actualStatus)
            .isEqualTo(expectedStatus);
    }
}
