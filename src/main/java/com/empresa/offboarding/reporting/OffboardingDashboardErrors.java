package com.empresa.offboarding.reporting;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=OffboardingDashboardController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OffboardingDashboardErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail invalid(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(
            error.getStatusCode(),error.getReason()==null?"Invalid report request.":error.getReason());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,"This dashboard is available to ADMIN and AUDITOR.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail failed(Exception error) {
        org.slf4j.LoggerFactory.getLogger(OffboardingDashboardErrors.class)
            .error("Offboarding dashboard failed",error);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,"Unable to load the dashboard.");
    }
}