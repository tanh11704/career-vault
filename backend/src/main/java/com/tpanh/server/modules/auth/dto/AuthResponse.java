package com.tpanh.server.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("user_id") String userId,
    @JsonProperty("full_name") String fullName,
    @JsonProperty("role") String role,
    @JsonProperty("avatar_url") String avatarUrl
) {
}
