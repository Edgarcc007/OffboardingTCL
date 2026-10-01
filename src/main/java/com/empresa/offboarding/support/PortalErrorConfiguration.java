package com.empresa.offboarding.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.List;

@Configuration
public class PortalErrorConfiguration implements WebMvcConfigurer {
    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(0,(request,response,handler,error)->{
            if(request.getRequestURI().startsWith("/api/")){
                String reference=PortalErrorSupport.reference(request);
                response.setHeader("X-Request-Id",reference);
                PortalErrorSupport.log(request,error);
            }
            return null;
        });
    }
}