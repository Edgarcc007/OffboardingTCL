package com.empresa.offboarding.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

public final class R8Errors {
    private R8Errors(){}
    public static ResponseEntity<ProblemDetail> response(
        Exception error,HttpServletRequest request
    ) {
        Object existing=request.getAttribute("offboarding.request.reference");
        String id=existing==null?UUID.randomUUID().toString():existing.toString();
        request.setAttribute("offboarding.request.reference",id);

        int status=500;
        String message="The operation could not be confirmed. Refresh before retrying.";
        if(error instanceof ResponseStatusException known){
            status=known.getStatusCode().value();
            message=known.getReason()==null?message:known.getReason();
        }else if(error instanceof AccessDeniedException){
            status=403;message="Your profile cannot perform this action.";
        }else if(error instanceof ErrorResponse known){
            status=known.getStatusCode().value();
            message="Check required values and use ISO dates (yyyy-MM-dd).";
        }else if(error instanceof IllegalArgumentException){
            status=400;message="Check the submitted values.";
        }

        org.slf4j.LoggerFactory.getLogger("Offboarding.ApiErrors")
            .error("APIREF={} {} {}",id,request.getMethod(),request.getRequestURI(),error);

        ProblemDetail detail=ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(status),message);
        detail.setProperty("requestId",id);
        detail.setProperty("operation",request.getMethod()+" "+request.getRequestURI());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .header("X-Request-Id",id).body(detail);
    }
}