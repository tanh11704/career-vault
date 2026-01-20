package com.tpanh.server.modules.auth.dto;

import com.tpanh.server.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record CreatePasswordRequest(
        @NotBlank(message = "New password is required")
        @StrongPassword
        String newPassword
) {
}
