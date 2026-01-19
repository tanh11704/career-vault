package com.tpanh.server.modules.auth.dto;

import com.tpanh.server.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    String fullname,

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    String email,

    @StrongPassword(message = "Password must comprise at least 8 characters, with uppercase, lowercase, numbers, and special symbols.")
    String password
) {
}
