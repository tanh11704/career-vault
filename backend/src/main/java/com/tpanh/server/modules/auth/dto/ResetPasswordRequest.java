package com.tpanh.server.modules.auth.dto;

import com.tpanh.server.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
    @NotBlank String token,
    @StrongPassword String newPassword
) {
}
