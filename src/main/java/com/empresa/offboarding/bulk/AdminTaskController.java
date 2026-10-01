package com.empresa.offboarding.bulk;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/bulk/admin-tasks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTaskController {
    private final AdminTaskOperations service;
    public AdminTaskController(AdminTaskOperations service){this.service=service;}

    @GetMapping("/session")
    public ResponseEntity<Map<String,String>> session(Authentication auth,CsrfToken csrf) {
        service.authorizeAdmin(auth);
        if(csrf==null)throw AdminTaskPolicy.error(503,"CSRF protection is unavailable.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("header",csrf.getHeaderName(),"token",csrf.getToken()));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<AdminTaskOperations.Preview> preview(
        @PathVariable("taskId") long taskId,Authentication auth
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.preview(taskId,auth));
    }

    @PostMapping("/{taskId}/complete-validate")
    public AdminTaskOperations.CombinedResult combine(
        @PathVariable("taskId") long taskId,
        @RequestBody AdminTaskOperations.CombinedRequest request,Authentication auth
    ) {
        return service.combine(taskId,request,auth);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> error(Exception error,HttpServletRequest request) {
        return com.empresa.offboarding.support.R8Errors.response(error,request);
    }
}