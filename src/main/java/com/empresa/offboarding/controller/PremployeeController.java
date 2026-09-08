package com.empresa.offboarding.controller;

import com.empresa.offboarding.dto.PremployeeDTO;
import com.empresa.offboarding.service.PremployeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/premployees")
public class PremployeeController {

    private final PremployeeService service;

    public PremployeeController(PremployeeService service) {
        this.service = service;
    }

    @GetMapping("/by-num/{num}")
    public ResponseEntity<PremployeeDTO> byNum(@PathVariable int num) {
        return service.findByEmployeeNum(num)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<PremployeeDTO> search(@RequestParam String name) {
        return service.searchByName(name);
    }
}