package com.harshaandra.helix.api.rest;

import com.harshaandra.helix.service.exception.ServiceExceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every error leaves this service as an RFC 7807 problem document, so a client parses one shape
 * whatever went wrong. The `type` URI is stable and documented — clients branch on it rather
 * than on a human-readable message that is free to change.
 *
 * Note what is deliberately NOT here: stack traces, SQL, entity names, or the raw exception
 * message from anything the caller does not already know. An error response is an information
 * disclosure surface (OWASP A01/A05); see docs/SECURITY.md.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE = "https://helix.harsha-andra.dev/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request);
        problem.setType(URI.create(BASE + "not-found"));
        problem.setProperty("resourceType", ex.getResourceType());
        problem.setProperty("identifier", ex.getIdentifier());
        return problem;
    }

    /**
     * 409, not 400. The request was well-formed and the caller was allowed to make it — the
     * state simply moved underneath them. A client can and should retry after reloading.
     */
    @ExceptionHandler(StaleClaimException.class)
    public ProblemDetail onStale(StaleClaimException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.CONFLICT, "Claim was modified by someone else",
                ex.getMessage(), request);
        problem.setType(URI.create(BASE + "stale-claim"));
        problem.setProperty("expectedVersion", ex.getExpectedVersion());
        problem.setProperty("actualVersion", ex.getActualVersion());
        problem.setProperty("recoveryAction", "RELOAD_AND_RETRY");
        return problem;
    }

    /** Hibernate's own optimistic-lock failure, for the paths that do not pre-check the version. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail onOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.debug("Optimistic lock failure on {}", request.getRequestURI(), ex);
        ProblemDetail problem = base(HttpStatus.CONFLICT, "Concurrent modification",
                "This record was changed by another user while you were editing it. "
                        + "Reload and try again.", request);
        problem.setType(URI.create(BASE + "concurrent-modification"));
        problem.setProperty("recoveryAction", "RELOAD_AND_RETRY");
        return problem;
    }

    /** 422: the request parsed and validated, but a domain rule says no. */
    @ExceptionHandler(IllegalStatusTransitionException.class)
    public ProblemDetail onIllegalTransition(IllegalStatusTransitionException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.UNPROCESSABLE_ENTITY, "Illegal status transition",
                ex.getMessage(), request);
        problem.setType(URI.create(BASE + "illegal-status-transition"));
        problem.setProperty("from", ex.getFrom().name());
        problem.setProperty("to", ex.getTo().name());
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail onBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation",
                ex.getMessage(), request);
        problem.setType(URI.create(BASE + "business-rule"));
        problem.setProperty("rule", ex.getRule());
        return problem;
    }

    /** Field-level validation. Returns every failure at once rather than the first one. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields were rejected.", request);
        problem.setType(URI.create(BASE + "validation"));

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // The underlying Jackson message can echo payload fragments back to the caller, so it
        // is logged and not returned.
        log.debug("Unreadable request body on {}", request.getRequestURI(), ex);
        ProblemDetail problem = base(HttpStatus.BAD_REQUEST, "Malformed request body",
                "The request body could not be parsed as JSON.", request);
        problem.setType(URI.create(BASE + "malformed-body"));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to perform this action.", request);
        problem.setType(URI.create(BASE + "forbidden"));
        return problem;
    }

    /**
     * The catch-all. Logs the real cause with a correlation id and returns none of it.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = java.util.UUID.randomUUID().toString();
        log.error("Unhandled exception [{}] on {} {}", correlationId,
                request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem = base(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Something went wrong. Quote the correlation id when reporting this.", request);
        problem.setType(URI.create(BASE + "internal"));
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    private static Map<String, String> describe(FieldError error) {
        Map<String, String> described = new LinkedHashMap<>();
        described.put("field", error.getField());
        described.put("message", error.getDefaultMessage());
        return described;
    }

    private static ProblemDetail base(HttpStatus status, String title, String detail,
                                      HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
