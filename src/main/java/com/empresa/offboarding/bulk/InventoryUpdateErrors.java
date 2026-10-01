package com.empresa.offboarding.bulk;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=InventoryUpdateController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InventoryUpdateErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(
            error.getStatusCode(),
            error.getReason()==null?"Unable to process the inventory.":error.getReason());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,"Only ADMIN can update the inventory.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail failed(Exception error) {
        org.slf4j.LoggerFactory.getLogger(InventoryUpdateErrors.class)
            .error("Inventory operation failed",error);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The operation could not be confirmed. Refresh the inventory status before retrying.");
    }
}