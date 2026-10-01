package com.empresa.offboarding.reporting;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/audit/offboarding-dashboard")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class OffboardingDashboardController {
    private final OffboardingDashboard dashboard;
    public OffboardingDashboardController(OffboardingDashboard dashboard){this.dashboard=dashboard;}

    @GetMapping("/options")
    public ResponseEntity<Map<String,Object>> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboard.filterOptions());
    }

    @GetMapping
    public ResponseEntity<OffboardingDashboard.Report> report(
        @RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(name="period",defaultValue="WEEK") OffboardingDashboard.Period period,
        @RequestParam(name="dimension",defaultValue="DEPARTMENT") OffboardingDashboard.Dimension dimension,
        @RequestParam(name="department",defaultValue="") String department,
        @RequestParam(name="area",defaultValue="") String area
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(dashboard.report(from,to,period,dimension,department,area));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> error(Exception error,HttpServletRequest request) {
        return com.empresa.offboarding.support.R8Errors.response(error,request);
    }
}