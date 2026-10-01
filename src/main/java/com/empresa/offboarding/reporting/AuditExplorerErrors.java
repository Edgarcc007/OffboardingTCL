package com.empresa.offboarding.reporting;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=AuditExplorerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditExplorerErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(
            error.getStatusCode(),error.getReason()==null?"Unable to complete the report.":error.getReason());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Your profile cannot access audit reports.");
    }

    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Check the dates and search parameters.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception error) {
        org.slf4j.LoggerFactory.getLogger(AuditExplorerErrors.class)
            .error("Audit report failed",error);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,"Unable to complete the report. No records were modified.");
    }
}