package com.tpanh.server.modules.auth.service;

import com.tpanh.server.modules.auth.dto.AuthResponse;
import com.tpanh.server.modules.auth.dto.GoogleLoginRequest;
import com.tpanh.server.modules.auth.dto.LoginRequest;
import com.tpanh.server.modules.auth.dto.RegisterRequest;
import com.tpanh.server.modules.auth.entity.RefreshToken;
import com.tpanh.server.modules.auth.entity.User;

public interface AuthService {

    void register(RegisterRequest request);

    void verifyEmail(String token);

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword);

    void resendVerification(String email);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithGoogle(GoogleLoginRequest request);

    void createPassword(String email, String newPassword);

    AuthResponse refreshToken(String requestRefreshToken);

    String generateAndSaveRefreshToken(User user);

    void revokeAllUserTokens(User user);

    void revokeRefreshToken(RefreshToken token);
}
