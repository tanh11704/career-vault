package com.tpanh.server.modules.auth.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String userId,
    String fullName,
    String role,
    String avatarUrl
) {
}
