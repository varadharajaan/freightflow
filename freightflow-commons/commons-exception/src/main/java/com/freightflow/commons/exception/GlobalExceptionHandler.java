package com.freightflow.commons.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Global exception handler for all FreightFlow REST APIs.
 *
 * <p>Translates domain exceptions into RFC 7807 Problem Detail responses.
 * This ensures consistent error formatting across all microservices
 * (Single Responsibility Principle — error translation is centralized here).</p>
 *
 * <p>Each exception type maps to a specific HTTP status code:</p>
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404</li>
 *   <li>{@link ValidationException} → 422</li>
 *   <li>{@link ConflictException} → 409</li>
 *   <li>{@link BusinessRuleViolationException} → 422</li>
 *   <li>{@link ExternalServiceException} → 502/503</li>
 *   <li>{@link MethodArgumentNotValidException} → 422 (Spring Bean Validation)</li>
 *   <li>{@link Exception} → 500 (catch-all safety net)</li>
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7807">RFC 7807 - Problem Details for HTTP APIs</a>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE_URI = "https://api.freightflow.com/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: errorCode={}, resourceType={}, resourceId={}",
                ex.getErrorCode(), ex.getResourceType(), ex.getResourceId());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + "resource-not-found"));
        problem.setTitle("Resource Not Found");
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("resourceType", ex.getResourceType());
        problem.setProperty("resourceId", ex.getResourceId());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        log.warn("Validation failed: errorCode={}, fieldErrors={}",
                ex.getErrorCode(), ex.getFieldErrors().size());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + "validation-error"));
        problem.setTitle("Validation Failed");
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("errors", ex.getFieldErrors());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        log.warn("Conflict: errorCode={}, currentState={}",
                ex.getErrorCode(), ex.getCurrentState());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + "conflict"));
        problem.setTitle("Conflict");
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("currentState", ex.getCurrentState());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleViolationException ex) {
        log.warn("Business rule violation: errorCode={}, rule={}",
                ex.getErrorCode(), ex.getRuleName());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + "business-rule-violation"));
        problem.setTitle("Business Rule Violation");
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("ruleName", ex.getRuleName());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalService(ExternalServiceException ex) {
        log.error("External service failure: errorCode={}, service={}, httpStatus={}",
                ex.getErrorCode(), ex.getServiceName(), ex.getHttpStatus(), ex);

        HttpStatus status = ex.getHttpStatus() == 504
                ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.BAD_GATEWAY;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + "external-service-failure"));
        problem.setTitle("External Service Failure");
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("serviceName", ex.getServiceName());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    /**
     * Handles Spring Bean Validation errors ({@code @Valid} annotation failures).
     * Translates MethodArgumentNotValidException into our standard ValidationException format.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ValidationException.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ValidationException.FieldError(
                        fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();

        log.warn("Bean validation failed: fieldErrors={}", fieldErrors.size());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Request body contains %d validation error(s)".formatted(fieldErrors.size()));
        problem.setType(URI.create(PROBLEM_BASE_URI + "validation-error"));
        problem.setTitle("Validation Failed");
        problem.setProperty("errorCode", "VALIDATION_ERROR");
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    /**
     * Catch-all handler for unexpected exceptions.
     *
     * <p>This is the safety net — any unhandled exception gets a generic 500 response.
     * The actual exception details are logged at ERROR level but NOT exposed to the client
     * (to prevent information leakage).</p>
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.");
        problem.setType(URI.create(PROBLEM_BASE_URI + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
