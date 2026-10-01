package com.empresa.offboarding.bulk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/api/bulk")
public class BulkWorkflowController {

    private final BulkWorkflow workflow;
    private final BulkPreviewController preview;
    private final ObjectMapper json;
    private final Semaphore validations = new Semaphore(2);

    public BulkWorkflowController(
        BulkWorkflow workflow, BulkPreviewController preview, ObjectMapper json
    ) {
        this.workflow=workflow; this.preview=preview; this.json=json;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> health() {
        return ResponseEntity.status(workflow.isReady() ? 200 : 503)
            .cacheControl(CacheControl.noStore())
            .body(Map.of(
                "status",workflow.isReady() ? "UP" : "STARTING",
                "module",BulkWorkflow.MODULE
            ));
    }

    @PostMapping(value="/batches/upload", consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public BulkWorkflow.Batch upload(
        HttpServletRequest request, Authentication auth,
        @RequestHeader(value="X-File-Name", defaultValue="bajas.xlsx") String name
    ) throws IOException {
        workflow.actor(auth);
        reserve();
        try {
            var prepared=preview.preview(request,auth,name).getBody();
            return workflow.upload(Objects.requireNonNull(prepared),name,auth);
        } finally { validations.release(); }
    }

    @GetMapping("/batches")
    public List<BulkWorkflow.Summary> list(
        @RequestParam(defaultValue="0") int page, Authentication auth
    ) {
        return workflow.list(page,auth);
    }

    @GetMapping("/batches/{id}")
    public BulkWorkflow.Batch get(@PathVariable UUID id, Authentication auth) {
        return workflow.get(id,auth);
    }

    @PutMapping(value="/batches/{id}", consumes=MediaType.APPLICATION_JSON_VALUE)
    public BulkWorkflow.Batch save(
        @PathVariable UUID id, HttpServletRequest request, Authentication auth
    ) throws IOException {
        workflow.actor(auth);
        reserve();
        try {
            if(request.getContentLengthLong()>8*1024*1024)
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            byte[] bytes=request.getInputStream().readNBytes(8*1024*1024+1);
            if(bytes.length>8*1024*1024)
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            BulkWorkflow.Input input;
            try { input=json.readValue(bytes,BulkWorkflow.Input.class); }
            catch(JsonProcessingException ex) {
                throw new IllegalArgumentException("Solicitud de guardado invalida.");
            }
            return workflow.save(id,input,auth);
        } finally { validations.release(); }
    }

    @PostMapping("/batches/{id}/confirm")
    public BulkWorkflow.Batch confirm(
        @PathVariable UUID id, @RequestParam long version, Authentication auth
    ) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Utiliza Bulk Offboarding desde Register new offboarding.");
    }

    @PostMapping("/batches/{id}/rows/{row}/register")
    public BulkWorkflow.Result register(
        @PathVariable UUID id, @PathVariable int row, Authentication auth
    ) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,"El registro por fila fue sustituido por Bulk Offboarding.");
    }

    @PostMapping("/batches/{id}/copy-pending")
    public BulkWorkflow.Batch copy(@PathVariable UUID id, Authentication auth) {
        return workflow.copyPending(id,auth);
    }

    private void reserve() {
        if(!validations.tryAcquire())
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,"Hay otras validaciones en proceso.");
    }
}