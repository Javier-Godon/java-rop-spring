package es.bluesolution.rop.spring;

import es.bluesolution.rop.core.FailureResultDescription;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;


import java.io.EOFException;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;

/**
 * Maps exceptions to appropriate HTTP status codes following established conventions.
 * This mapper provides comprehensive coverage of JDK, Spring, JPA, and other framework exceptions.
 * 
 * The mapping strategy follows Railway-Oriented Programming principles by converting
 * exceptions from external libraries into appropriate HTTP responses without throwing
 * exceptions within our domain logic.
 */
public final class HttpStatusMapper {

    private static final Map<Class<? extends Throwable>, HttpStatus> EXCEPTION_TO_STATUS_MAP;

    static {
        EXCEPTION_TO_STATUS_MAP = new HashMap<>();

        // ---- JDK: Client errors (400 Bad Request) ----
        EXCEPTION_TO_STATUS_MAP.put(IllegalArgumentException.class, HttpStatus.BAD_REQUEST);
        EXCEPTION_TO_STATUS_MAP.put(NumberFormatException.class, HttpStatus.BAD_REQUEST);
        EXCEPTION_TO_STATUS_MAP.put(IndexOutOfBoundsException.class, HttpStatus.BAD_REQUEST);
        EXCEPTION_TO_STATUS_MAP.put(UnsupportedOperationException.class, HttpStatus.BAD_REQUEST);
        EXCEPTION_TO_STATUS_MAP.put(EOFException.class, HttpStatus.BAD_REQUEST);

        // ---- JDK: Conflict states (409 Conflict) ----
        EXCEPTION_TO_STATUS_MAP.put(IllegalStateException.class, HttpStatus.CONFLICT);

        // ---- JDK: Not found (404 Not Found) ----
        EXCEPTION_TO_STATUS_MAP.put(NoSuchElementException.class, HttpStatus.NOT_FOUND);
        EXCEPTION_TO_STATUS_MAP.put(FileNotFoundException.class, HttpStatus.NOT_FOUND);

        // ---- JDK: Forbidden (403 Forbidden) ----
        EXCEPTION_TO_STATUS_MAP.put(SecurityException.class, HttpStatus.FORBIDDEN);

        // ---- JDK: Service unavailable/timeout ----
        EXCEPTION_TO_STATUS_MAP.put(ConnectException.class, HttpStatus.SERVICE_UNAVAILABLE);
        EXCEPTION_TO_STATUS_MAP.put(SocketTimeoutException.class, HttpStatus.GATEWAY_TIMEOUT);
        EXCEPTION_TO_STATUS_MAP.put(TimeoutException.class, HttpStatus.REQUEST_TIMEOUT);

        // ---- JDK: Internal server error (500) ----
        EXCEPTION_TO_STATUS_MAP.put(NullPointerException.class, HttpStatus.INTERNAL_SERVER_ERROR);

        // ---- Spring Web/MVC: Validation errors (400 Bad Request) ----
        EXCEPTION_TO_STATUS_MAP.put(HttpMessageNotReadableException.class, HttpStatus.BAD_REQUEST);
        putIfPresent("org.springframework.web.bind.MissingServletRequestParameterException", HttpStatus.BAD_REQUEST);
        putIfPresent("org.springframework.web.bind.MissingRequestHeaderException", HttpStatus.BAD_REQUEST);
        putIfPresent("org.springframework.web.bind.ServletRequestBindingException", HttpStatus.BAD_REQUEST);

        // ---- Spring Web/MVC: Validation errors (422 Unprocessable Entity) ----
        EXCEPTION_TO_STATUS_MAP.put(MethodArgumentNotValidException.class, HttpStatus.valueOf(422));
        putIfPresent("jakarta.validation.ConstraintViolationException", HttpStatus.valueOf(422));

        // ---- Spring Web/MVC: Not found (404) ----
        putIfPresent("org.springframework.web.servlet.NoHandlerFoundException", HttpStatus.NOT_FOUND);

        // ---- Spring Web/MVC: Method not allowed (405) ----
        putIfPresent("org.springframework.web.HttpRequestMethodNotSupportedException", HttpStatus.METHOD_NOT_ALLOWED);

        // ---- Spring Web/MVC: Media type issues ----
        putIfPresent("org.springframework.web.HttpMediaTypeNotAcceptableException", HttpStatus.NOT_ACCEPTABLE);
        putIfPresent("org.springframework.web.HttpMediaTypeNotSupportedException", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        // ---- Spring WebFlux ----
        EXCEPTION_TO_STATUS_MAP.put(ServerWebInputException.class, HttpStatus.BAD_REQUEST);
        EXCEPTION_TO_STATUS_MAP.put(ServerErrorException.class, HttpStatus.INTERNAL_SERVER_ERROR);

        // ---- Spring Security ----
        putIfPresent("org.springframework.security.core.AuthenticationException", HttpStatus.UNAUTHORIZED);
        putIfPresent("org.springframework.security.authentication.BadCredentialsException", HttpStatus.UNAUTHORIZED);
        putIfPresent("org.springframework.security.access.AccessDeniedException", HttpStatus.FORBIDDEN);

        // ---- JPA/Hibernate: Not found ----
        putIfPresent("jakarta.persistence.EntityNotFoundException", HttpStatus.NOT_FOUND);
        EXCEPTION_TO_STATUS_MAP.put(EmptyResultDataAccessException.class, HttpStatus.NOT_FOUND);

        // ---- JPA/Hibernate: Conflict ----
        EXCEPTION_TO_STATUS_MAP.put(DataIntegrityViolationException.class, HttpStatus.CONFLICT);
        EXCEPTION_TO_STATUS_MAP.put(DuplicateKeyException.class, HttpStatus.CONFLICT);

        // ---- JAX-RS ----
        putIfPresent("jakarta.ws.rs.NotFoundException", HttpStatus.NOT_FOUND);

        // ---- Top-level fallbacks (should be last due to inheritance hierarchy) ----
        EXCEPTION_TO_STATUS_MAP.put(RuntimeException.class, HttpStatus.INTERNAL_SERVER_ERROR);
        EXCEPTION_TO_STATUS_MAP.put(Exception.class, HttpStatus.INTERNAL_SERVER_ERROR);
        EXCEPTION_TO_STATUS_MAP.put(Throwable.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Registers a mapping only when the exception type is on the classpath. Used for optional dependencies
     * (Servlet/Spring MVC, Spring Security, JPA, Bean Validation, JAX-RS) so that loading this class never
     * fails with {@link NoClassDefFoundError} in applications that do not use them.
     */
    @SuppressWarnings("unchecked")
    private static void putIfPresent(String className, HttpStatus value) {
        try {
            Class<?> type = Class.forName(className, false, HttpStatusMapper.class.getClassLoader());
            if (Throwable.class.isAssignableFrom(type)) {
                EXCEPTION_TO_STATUS_MAP.put((Class<? extends Throwable>) type, value);
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // optional dependency absent
        }
    }

    private HttpStatusMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Maps a FailureResultDescription to appropriate HTTP status.
     * 
     * This method provides comprehensive mapping by:
     * 1. Special handling for ResponseStatusException (extracts embedded status)
     * 2. Exception-specific mapping using inheritance hierarchy traversal
     * 3. Fallback to error code mapping
     * 4. Final fallback to 500 Internal Server Error
     *
     * @param failure The failure description to map
     * @return The appropriate HttpStatus, never null
     */
    public static HttpStatus mapToHttpStatus(FailureResultDescription failure) {
        // Special case: Spring's ResponseStatusException contains its own status
        if (failure.exception() instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        }

        // If we have an exception, try to map it directly (more specific than error code)
        if (failure.exception() != null) {
            HttpStatus status = mapExceptionToStatus(failure.exception());
            if (status != null) {
                return status;
            }
        }

        // Fall back to error code mapping
        return mapErrorCodeToStatus(failure.code());
    }

    /**
     * Maps an exception directly to HTTP status using inheritance hierarchy.
     * This ensures we never have an unmapped exception by walking up the class hierarchy.
     */
    private static HttpStatus mapExceptionToStatus(Exception exception) {
        Class<?> cls = exception.getClass();
        while (cls != null && cls != Object.class) {
            HttpStatus status = EXCEPTION_TO_STATUS_MAP.get(cls);
            if (status != null) {
                return status;
            }
            cls = cls.getSuperclass();
        }
        return null; // Let error code mapping handle it
    }

    /**
     * Maps Railway framework error codes to HTTP status codes.
     * MANDATORY: Exhaustive switch ensures all error codes are mapped
     */
    private static HttpStatus mapErrorCodeToStatus(FailureResultDescription.ErrorCode errorCode) {
        return switch (errorCode) {
            // Client-side errors (4xx HTTP range)
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;           // 400
            case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;      // 401
            case AUTHORIZATION_ERROR -> HttpStatus.FORBIDDEN;          // 403
            case NOT_FOUND -> HttpStatus.NOT_FOUND;                    // 404
            case BUSINESS_RULE_ERROR -> HttpStatus.CONFLICT;           // 409
            case RATE_LIMIT_ERROR -> HttpStatus.TOO_MANY_REQUESTS;     // 429
            
            // Server-side errors (5xx HTTP range)
            case TECHNICAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;  // 500
            case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;   // 500
            case CONFIGURATION_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR; // 500
            case EXTERNAL_SERVICE_ERROR -> HttpStatus.BAD_GATEWAY;     // 502
            case SERVICE_UNAVAILABLE_ERROR -> HttpStatus.SERVICE_UNAVAILABLE; // 503
            case TIMEOUT_ERROR -> HttpStatus.GATEWAY_TIMEOUT;          // 504
            case UNKNOWN_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;    // 500
        };
    }
}