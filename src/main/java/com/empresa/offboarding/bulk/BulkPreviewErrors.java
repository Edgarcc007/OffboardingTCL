package com.empresa.offboarding.bulk;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {BulkPreviewController.class, BulkWorkflowController.class, BulkSimpleController.class, AssetCatalogController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BulkPreviewErrors {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of(
                "message",
                ex.getReason() == null ? "No se pudo procesar la solicitud."
                                       : ex.getReason()
            ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
            "message", ex.getMessage() == null ? "Archivo invalido." : ex.getMessage()
        ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> database(DataAccessException ex) {
        return ResponseEntity.status(503).body(Map.of(
            "message", "No se pudo consultar la informacion necesaria. " +
                       "No se registraron bajas."
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> unexpectedBulkError(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(BulkPreviewErrors.class)
            .error("Error del importador", ex);
        return ResponseEntity.status(500).body(Map.of(
            "message", "No se pudo completar la operacion. Consulta el estado del lote antes de reintentar."
        ));
    }
}