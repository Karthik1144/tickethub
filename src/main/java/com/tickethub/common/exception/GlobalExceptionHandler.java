package com.tickethub.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail pd = base(ex.getErrorCode(), ex.getMessage(), request);
        ex.getProperties().forEach(pd::setProperty);
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(),
                                          f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                                          (a, b) -> a, LinkedHashMap::new));
        ProblemDetail pd = base(ErrorCode.VALIDATION_FAILED, "Request contains invalid fields", request);
        pd.setProperty("fields", fields);
        return pd;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return base(ErrorCode.CONCURRENT_UPDATE, "The resource changed while you were working on it", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return base(ErrorCode.FORBIDDEN, "You do not have access to this resource", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return base(ErrorCode.UNAUTHENTICATED, "Authentication required", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return base(ErrorCode.INTERNAL_ERROR, "Unexpected error", request);
    }

    private ProblemDetail base(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
        pd.setTitle(code.getTitle());
        pd.setType(URI.create("https://tickethub.dev/errors/" + code.name().toLowerCase().replace('_', '-')));
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", Instant.now().toString());
        if (request != null) {
            pd.setProperty("path", request.getRequestURI());
        }
        return pd;
    }
}
