package com.tpanh.server.modules.auth.mapper;

import com.tpanh.server.modules.auth.dto.AuthResponse;
import com.tpanh.server.modules.auth.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-01-19T17:30:03+0700",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.9 (Eclipse Adoptium)"
)
@Component
public class AuthMapperImpl implements AuthMapper {

    @Override
    public AuthResponse toAuthResponse(User user, String accessToken, String refreshToken) {
        if ( user == null && accessToken == null && refreshToken == null ) {
            return null;
        }

        String userId = null;
        String fullName = null;
        String role = null;
        String avatarUrl = null;
        if ( user != null ) {
            if ( user.getId() != null ) {
                userId = user.getId().toString();
            }
            fullName = user.getFullName();
            if ( user.getRole() != null ) {
                role = user.getRole().name();
            }
            avatarUrl = user.getAvatarUrl();
        }
        String accessToken1 = null;
        accessToken1 = accessToken;
        String refreshToken1 = null;
        refreshToken1 = refreshToken;

        AuthResponse authResponse = new AuthResponse( accessToken1, refreshToken1, userId, fullName, role, avatarUrl );

        return authResponse;
    }
}
