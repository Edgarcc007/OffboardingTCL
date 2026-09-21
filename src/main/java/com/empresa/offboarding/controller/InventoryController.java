package com.empresa.offboarding.controller;

import com.empresa.offboarding.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * GET /api/inventory/lookup?q=searchTerm
     *
     * Returns the first match with found=true and all column values,
     * or found=false if nothing matches.
     */
    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookup(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("found", false);
            resp.put("message", "Query too short (min 2 chars)");
            return ResponseEntity.ok(resp);
        }

        List<Map<String, String>> results = inventoryService.search(q.trim(), 1);

        Map<String, Object> resp = new LinkedHashMap<>();
        if (!results.isEmpty()) {
            Map<String, String> row = results.get(0);
            resp.put("found", true);

            // Try to find common fields for the frontend tag display
            resp.put("assetTag", findValue(row, "asset", "tag", "etiqueta", "activo"));
            resp.put("description", findValue(row, "description", "descripcion", "desc", "equipo", "model"));
            resp.put("status", findValue(row, "status", "estado", "estatus"));
            resp.put("serial", findValue(row, "serial", "serie", "s/n"));
            resp.put("assignedTo", findValue(row, "assigned", "asignado", "usuario", "employee", "empleado"));

            // Also return ALL columns so frontend has everything
            resp.put("allFields", row);
        } else {
            resp.put("found", false);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/inventory/search?q=searchTerm&limit=10
     *
     * Returns multiple matches (for future use or autocomplete).
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {

        List<Map<String, String>> results = inventoryService.search(q, Math.min(limit, 50));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("count", results.size());
        resp.put("results", results);
        resp.put("headers", inventoryService.getHeaders());
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/inventory/status
     *
     * Health check: how many rows loaded, column names.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rowCount", inventoryService.getRowCount());
        resp.put("headers", inventoryService.getHeaders());
        return ResponseEntity.ok(resp);
    }

    /**
     * Finds the first non-empty value from a row where the column header
     * contains any of the given keywords (case-insensitive).
     */
    private String findValue(Map<String, String> row, String... keywords) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String header = entry.getKey().toLowerCase();
            for (String kw : keywords) {
                if (header.contains(kw.toLowerCase()) && !entry.getValue().isEmpty()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
