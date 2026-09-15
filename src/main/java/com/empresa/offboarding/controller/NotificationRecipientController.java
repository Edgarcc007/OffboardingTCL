package com.empresa.offboarding.controller;

import com.empresa.offboarding.dto.NotificationRecipientRequest;
import com.empresa.offboarding.dto.NotificationRecipientResponse;
import com.empresa.offboarding.service.NotificationRecipientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/notification-recipients")
@RequiredArgsConstructor
public class NotificationRecipientController {

    private final NotificationRecipientService service;

    @GetMapping
    public List<NotificationRecipientResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationRecipientResponse create(
            @Valid @RequestBody
            NotificationRecipientRequest request,
            Principal principal
    ) {
        return service.create(
                request,
                principal.getName()
        );
    }

    @PutMapping("/{id}")
    public NotificationRecipientResponse update(
            @PathVariable Long id,
            @Valid @RequestBody
            NotificationRecipientRequest request,
            Principal principal
    ) {
        return service.update(
                id,
                request,
                principal.getName()
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            Principal principal
    ) {
        service.delete(id, principal.getName());
    }
}
