package com.empresa.offboarding.reporting;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/audit/explorer")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class AuditExplorerController {
    private final AuditExplorer explorer;

    public AuditExplorerController(AuditExplorer explorer) {
        this.explorer=explorer;
    }

    @GetMapping
    public ResponseEntity<AuditExplorer.Result> search(
        @RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to,
        @RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="ALL") AuditExplorer.SearchField field,
        @RequestParam(required=false) Long snapshot,
        @RequestParam(defaultValue="0") int offset
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            explorer.search(new AuditExplorer.Filter(from,to,q,field),snapshot,offset));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
        @RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to,
        @RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="ALL") AuditExplorer.SearchField field,
        @RequestParam(required=false) Long snapshot
    ) throws Exception {
        byte[] bytes=explorer.export(new AuditExplorer.Filter(from,to,q,field),snapshot);

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("Offboarding-Audit.xlsx").build().toString())
            .body(bytes);
    }
}