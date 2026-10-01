package com.empresa.offboarding.bulk;

import org.springframework.core.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=AdminMaintenanceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminMaintenanceErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(),
            error.getReason()==null?"Operation unavailable.":error.getReason());
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Only ADMIN can perform maintenance.");
    }
    @ExceptionHandler(Exception.class)
    public ProblemDetail failed(Exception error) {
        org.slf4j.LoggerFactory.getLogger(AdminMaintenanceErrors.class)
            .error("Administrative maintenance could not be confirmed",error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "The operation could not be confirmed. Refresh the record before retrying.");
    }
}