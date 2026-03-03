package com.eduai.backend.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits") String phone,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password
) {}
