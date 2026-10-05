package com.tickethub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank
            @Size(min = 8, max = 72, message = "password must be at least 8 characters")
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",
                    message = "password must contain upper, lower, number and special character")
            String password,
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @Pattern(regexp = "^$|\\+?[0-9]{10,15}$", message = "phone must contain 10 to 15 digits")
            String phone) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}

    public record UserResponse(Long id, String email, String fullName, String phone, String role) {}
}
