package com.empresa.offboarding.bulk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/api/bulk/simple")
public class BulkSimpleController {
    private final BulkWorkflow workflow;
    private final ObjectMapper json;
    private final Semaphore slots=new Semaphore(2);

    public BulkSimpleController(BulkWorkflow workflow,ObjectMapper json) {
        this.workflow=workflow;this.json=json;
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String,Object>> session(
        Authentication auth,CsrfToken csrf
    ) {
        var owner=workflow.actor(auth);
        if(csrf==null) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,"Proteccion CSRF no disponible.");

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "owner",owner,"header",csrf.getHeaderName(),"token",csrf.getToken(),
            "columns",BulkExcelReader.HEADERS));
    }

    @PostMapping(value="/upload",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public BulkWorkflow.SimpleResult upload(
        @RequestHeader("X-Request-ID") UUID id,
        @RequestHeader("X-File-Name") String fileName,
        HttpServletRequest request,Authentication auth
    ) throws IOException {
        workflow.actor(auth);
        reserve();
        try {
            String name=URLDecoder.decode(fileName,StandardCharsets.UTF_8);
            if(!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                throw new IllegalArgumentException("Selecciona un archivo .xlsx.");
            }
            return workflow.simpleUpload(id,read(request),name,auth);
        } finally { slots.release(); }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BulkWorkflow.SimpleResult> get(
        @PathVariable UUID id,Authentication auth
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(workflow.simpleGet(id,auth));
    }

    @PostMapping(value="/{id}/validate",consumes=MediaType.APPLICATION_JSON_VALUE)
    public BulkWorkflow.SimpleResult validate(
        @PathVariable UUID id,HttpServletRequest request,Authentication auth
    ) throws IOException {
        workflow.actor(auth);
        reserve();
        try {
            BulkWorkflow.Input input;
            try { input=json.readValue(read(request),BulkWorkflow.Input.class); }
            catch(JsonProcessingException ex) {
                throw new IllegalArgumentException("Solicitud de correccion invalida.");
            }
            return workflow.simpleValidate(id,input,auth);
        } finally { slots.release(); }
    }

    @PostMapping("/{id}/register")
    public BulkWorkflow.SimpleResult register(
        @PathVariable UUID id,@RequestParam long version,Authentication auth
    ) {
        workflow.actor(auth);
        reserve();
        try {
            var result=workflow.simpleRegister(id,version,auth);
            return result.registered()?workflow.simpleSendMail(id,auth):result;
        } finally { slots.release(); }
    }

    @PostMapping("/{id}/mail")
    public BulkWorkflow.SimpleResult mail(
        @PathVariable UUID id,Authentication auth
    ) {
        return workflow.simpleSendMail(id,auth);
    }

    private void reserve() {
        if(!slots.tryAcquire()) throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,"Hay otros lotes en proceso. Intenta nuevamente.");
    }

    private byte[] read(HttpServletRequest request) throws IOException {
        int maximum=8*1024*1024;
        if(request.getContentLengthLong()>maximum)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(maximum+1);
        if(bytes.length>maximum)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        return bytes;
    }

    /* R4_SKIP_ACTIVE_ENDPOINT */
    @PostMapping(value="/{id}/skip-active",consumes=MediaType.APPLICATION_JSON_VALUE)
    public BulkWorkflow.SimpleResult skipActive(
        @PathVariable UUID id,HttpServletRequest request,Authentication auth
    ) throws IOException {
        workflow.actor(auth);
        reserve();
        try {
            BulkWorkflow.Input input=json.readValue(read(request),BulkWorkflow.Input.class);
            return workflow.simpleSkipActive(id,input,auth);
        } finally { slots.release(); }
    }
}