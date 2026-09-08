package com.empresa.offboarding.controller;

import com.empresa.offboarding.dto.CompleteTaskRequest;
import com.empresa.offboarding.dto.CreateOffboardingRequest;
import com.empresa.offboarding.dto.OffboardingResponse;
import com.empresa.offboarding.dto.ReopenTaskRequest;
import com.empresa.offboarding.dto.TaskResponse;
import com.empresa.offboarding.dto.ValidateAssetTaskRequest;
import com.empresa.offboarding.service.OffboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OffboardingController {

    private final OffboardingService offboardingService;

    @PostMapping("/offboardings")
    @ResponseStatus(HttpStatus.CREATED)
    public OffboardingResponse create(
            @Valid @RequestBody
            CreateOffboardingRequest request,
            Principal principal
    ) {
        return offboardingService.create(
                request,
                principal.getName()
        );
    }

    @GetMapping("/offboardings")
    public List<OffboardingResponse> findAll() {
        return offboardingService.findAll();
    }

    @GetMapping("/offboardings/{id}")
    public OffboardingResponse findById(
            @PathVariable Long id
    ) {
        return offboardingService.findById(id);
    }

    @PatchMapping("/tasks/{taskId}/complete")
    public TaskResponse completeTask(
            @PathVariable Long taskId,
            @Valid @RequestBody
            CompleteTaskRequest request,
            Principal principal
    ) {
        return offboardingService.completeTask(
                taskId,
                request,
                principal.getName()
        );
    }

    @PatchMapping("/tasks/{taskId}/validate")
    public TaskResponse validateTask(
            @PathVariable Long taskId,
            @Valid @RequestBody
            ValidateAssetTaskRequest request,
            Principal principal
    ) {
        return offboardingService.validateTask(
                taskId,
                request,
                principal.getName()
        );
    }

    @PatchMapping("/tasks/{taskId}/reopen")
    public TaskResponse reopenTask(
            @PathVariable Long taskId,
            @Valid @RequestBody
            ReopenTaskRequest request,
            Principal principal
    ) {
        return offboardingService.reopenTask(
                taskId,
                request,
                principal.getName()
        );
    }
}
