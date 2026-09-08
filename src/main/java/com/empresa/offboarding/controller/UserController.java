package com.empresa.offboarding.controller;

import com.empresa.offboarding.dto.CreateUserRequest;
import com.empresa.offboarding.dto.ResetUserPasswordRequest;
import com.empresa.offboarding.dto.UpdateUserRequest;
import com.empresa.offboarding.dto.UserResponse;
import com.empresa.offboarding.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            @Valid @RequestBody
            CreateUserRequest request,
            Principal principal
    ) {
        return userService.create(
                request,
                principal.getName()
        );
    }

    @GetMapping
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody
            UpdateUserRequest request,
            Principal principal
    ) {
        return userService.update(
                id,
                request,
                principal.getName()
        );
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody
            ResetUserPasswordRequest request,
            Principal principal
    ) {
        userService.resetPassword(
                id,
                request,
                principal.getName()
        );
    }
}
