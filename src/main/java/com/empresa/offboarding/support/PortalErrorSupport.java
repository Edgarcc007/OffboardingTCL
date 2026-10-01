package com.empresa.offboarding.support;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.util.UUID;

public final class PortalErrorSupport {
    public static final String ATTRIBUTE="offboarding.request.reference";
    private static final Logger LOG=LoggerFactory.getLogger("Offboarding.ApiErrors");

    private PortalErrorSupport(){}

    public static String reference(HttpServletRequest request) {
        Object existing=request.getAttribute(ATTRIBUTE);
        if(existing!=null)return existing.toString();
        String value=UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE,value);
        return value;
    }

    private static SQLException sql(Throwable error) {
        Throwable current=error;
        for(int i=0;i<30&&current!=null;i++){
            if(current instanceof SQLException found)return found;
            if(current.getCause()==current)break;
            current=current.getCause();
        }
        return null;
    }

    public static int status(Throwable error) {
        if(error instanceof AccessDeniedException)return 403;
        if(error instanceof AuthenticationException)return 401;
        if(error instanceof ErrorResponse response)return response.getStatusCode().value();
        SQLException sql=sql(error);
        if(sql!=null&&sql.getSQLState()!=null){
            if(sql.getSQLState().startsWith("23"))return 409;
            if("42501".equals(sql.getSQLState()))return 403;
            if("55P03".equals(sql.getSQLState())||"40P01".equals(sql.getSQLState()))return 409;
        }
        if(error instanceof IllegalArgumentException)return 400;
        return 500;
    }

    public static void log(HttpServletRequest request,Throwable error) {
        if(Boolean.TRUE.equals(request.getAttribute("offboarding.error.logged")))return;
        request.setAttribute("offboarding.error.logged",true);
        String id=reference(request);
        if(status(error)>=500)
            LOG.error("APIREF={} {} {}",id,request.getMethod(),request.getRequestURI(),error);
        else
            LOG.warn("APIREF={} {} {} status={} exception={}",
                id,request.getMethod(),request.getRequestURI(),status(error),
                error.getClass().getSimpleName());
    }

    public static ResponseEntity<ProblemDetail> response(
        Exception error,HttpServletRequest request
    ) {
        log(request,error);
        int status=status(error);
        String message;

        if(error instanceof ResponseStatusException known&&known.getReason()!=null){
            message=known.getReason();
        }else if(status==401){
            message="Your session is unavailable. Sign in again.";
        }else if(status==403){
            message="This operation is not permitted. Check your profile and session.";
        }else if(status==400){
            message="Check the required fields, date format and submitted values.";
        }else if(status==409){
            SQLException sql=sql(error);
            String internal=sql==null?"":String.valueOf(sql.getMessage());
            if(internal.contains("ck_offboarding_task_segregation"))
                message="The database requires validation by a different account from the one that completed the task.";
            else
                message="The record changed, is busy, or a database integrity rule prevented the operation. Refresh before retrying.";
        }else{
            message="The server could not confirm the operation. Refresh the record before retrying; use the reference to review the server log.";
        }

        String id=reference(request);
        ProblemDetail detail=ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(status),message);
        detail.setTitle(status>=500?"Operation could not be confirmed":"Request not completed");
        detail.setProperty("requestId",id);
        detail.setProperty("operation",request.getMethod()+" "+request.getRequestURI());

        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .header("X-Request-Id",id).body(detail);
    }
}