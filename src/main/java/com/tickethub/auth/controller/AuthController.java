package com.tickethub.auth.controller;

import com.tickethub.auth.dto.AuthDtos.*;
import com.tickethub.auth.security.CurrentUser;
import com.tickethub.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/auth/register")
    @Operation(summary = "Register a new account")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in and receive tokens")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "Rotate the refresh token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Revoke all refresh tokens for the current user")
    public ResponseEntity<Void> logout() {
        authService.logout(currentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me")
    @Operation(summary = "Current user profile")
    public UserResponse me() {
        return authService.profile(currentUser.requireId());
    }
}
