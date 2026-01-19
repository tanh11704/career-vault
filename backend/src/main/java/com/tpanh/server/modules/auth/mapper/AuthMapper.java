package com.tpanh.server.modules.auth.mapper;

import com.tpanh.server.modules.auth.dto.AuthResponse;
import com.tpanh.server.modules.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    AuthResponse toAuthResponse(User user, String accessToken, String refreshToken);
}
