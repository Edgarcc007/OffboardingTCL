package com.empresa.offboarding.bulk;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/bulk/admin-maintenance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaintenanceController {
    private final AdminMaintenance service;
    public AdminMaintenanceController(AdminMaintenance service){this.service=service;}

    @GetMapping("/session")
    public ResponseEntity<Map<String,String>> session(Authentication auth,CsrfToken csrf) {
        service.authorize(auth);
        if(csrf==null)throw new IllegalStateException("CSRF protection is unavailable.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("header",csrf.getHeaderName(),"token",csrf.getToken()));
    }

    @GetMapping("/record/{kind}/{id}")
    public ResponseEntity<AdminMaintenance.View> get(
        @PathVariable("kind") String kind,@PathVariable("id") long id,Authentication auth
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.get(kind,id,auth));
    }

    @PostMapping("/record/{kind}/{id}/{action}")
    public AdminMaintenance.Result change(
        @PathVariable("kind") String kind,@PathVariable("id") long id,@PathVariable("action") String action,
        @RequestBody AdminMaintenance.Change request,Authentication auth
    ) {
        return service.change(kind,id,action,request,auth);
    }

    /* UX2_LOCAL_ERROR_HANDLER */
    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<org.springframework.http.ProblemDetail>
    portalErrorHandler(Exception error,jakarta.servlet.http.HttpServletRequest request) {
        return com.empresa.offboarding.support.PortalErrorSupport.response(error,request);
    }
}