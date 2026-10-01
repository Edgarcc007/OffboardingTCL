package com.empresa.offboarding.support;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+30)
public class PortalRequestTraceFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,HttpServletResponse response,FilterChain chain
    ) throws ServletException,IOException {
        if(!request.getRequestURI().startsWith("/api/")){
            chain.doFilter(request,response);return;
        }

        String reference=PortalErrorSupport.reference(request);
        response.setHeader("X-Request-Id",reference);
        try{
            chain.doFilter(request,response);
        }finally{
            if(response.getStatus()>=400&&
                !Boolean.TRUE.equals(request.getAttribute("offboarding.error.logged"))){
                org.slf4j.LoggerFactory.getLogger("Offboarding.ApiErrors").warn(
                    "APIREF={} {} {} status={}",reference,request.getMethod(),
                    request.getRequestURI(),response.getStatus());
            }
        }
    }
}