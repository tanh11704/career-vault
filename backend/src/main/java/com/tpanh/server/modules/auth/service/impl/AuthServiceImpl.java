package com.tpanh.server.modules.auth.service.impl;

import com.tpanh.server.common.exception.BusinessLogicException;
import com.tpanh.server.modules.auth.dto.AuthResponse;
import com.tpanh.server.modules.auth.dto.LoginRequest;
import com.tpanh.server.modules.auth.dto.RegisterRequest;
import com.tpanh.server.modules.auth.entity.RefreshToken;
import com.tpanh.server.modules.auth.entity.User;
import com.tpanh.server.modules.auth.mapper.AuthMapper;
import com.tpanh.server.modules.auth.repository.RefreshTokenRepository;
import com.tpanh.server.modules.auth.repository.UserRepository;
import com.tpanh.server.modules.auth.security.CustomUserDetails;
import com.tpanh.server.modules.auth.service.AuthService;
import com.tpanh.server.modules.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuthMapper authMapper;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessLogicException("Email already exists: " + request.email());
        }

        var user = User.builder()
                .fullName(request.fullname())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .isActive(true)
                .build();
        var savedUser = userRepository.saveAndFlush(user);

        var jwtToken = jwtService.generateToken(new CustomUserDetails(savedUser));
        var refreshToken = generateAndSaveRefreshToken(savedUser);

        return authMapper.toAuthResponse(savedUser, jwtToken, refreshToken);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        revokeAllUserTokens(user); // Chỉ cho phép đăng nhập 1 thiết bị

        var jwtToken = jwtService.generateToken(new CustomUserDetails(user));
        var refreshToken = generateAndSaveRefreshToken(user);

        return authMapper.toAuthResponse(user, jwtToken, refreshToken);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String requestRefreshToken) {
        // 1. Tìm token trong DB
        var storedToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new BusinessLogicException("Refresh token not found"));

        // 2. Kiểm tra tính hợp lệ
        if (storedToken.isRevoked()) {
            revokeAllUserTokens(storedToken.getUser());
            throw new BusinessLogicException("Refresh token has been revoked. Please login again.");
        }

        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            throw new BusinessLogicException("Refresh token expired");
        }

        User user = storedToken.getUser();
        var customUserDetails = new CustomUserDetails(user);

        revokeRefreshToken(storedToken);
        var newAccessToken = jwtService.generateToken(customUserDetails);
        var newRefreshToken = generateAndSaveRefreshToken(user);

        return authMapper.toAuthResponse(user, newAccessToken, newRefreshToken);
    }

    @Override
    public String generateAndSaveRefreshToken(User user) {
        var refreshToken = jwtService.generateRefreshToken(new CustomUserDetails(user));
        var tokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiryDate(Instant.now().plusMillis(refreshExpiration))
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenEntity);
        return refreshToken;
    }

    @Override
    public void revokeAllUserTokens(User user) {
        var validUserTokens = refreshTokenRepository.findAllValidTokenByUser(user.getId());
        if (validUserTokens.isEmpty()) return;
        validUserTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(validUserTokens);
    }

    @Override
    public void revokeRefreshToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }
}
