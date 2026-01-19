package com.tpanh.server.modules.auth.service.impl;

import com.tpanh.server.common.exception.BusinessLogicException;
import com.tpanh.server.common.exception.ResourceNotFoundException;
import com.tpanh.server.common.service.EmailService;
import com.tpanh.server.modules.auth.dto.AuthResponse;
import com.tpanh.server.modules.auth.dto.LoginRequest;
import com.tpanh.server.modules.auth.dto.RegisterRequest;
import com.tpanh.server.modules.auth.entity.RefreshToken;
import com.tpanh.server.modules.auth.entity.User;
import com.tpanh.server.modules.auth.entity.VerificationCode;
import com.tpanh.server.modules.auth.enums.TokenType;
import com.tpanh.server.modules.auth.mapper.AuthMapper;
import com.tpanh.server.modules.auth.repository.RefreshTokenRepository;
import com.tpanh.server.modules.auth.repository.UserRepository;
import com.tpanh.server.modules.auth.repository.VerificationCodeRepository;
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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuthMapper authMapper;
    private final EmailService emailService;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    @Value("${application.api.base-url}")
    private String baseUrl;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
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

        String code = generateAndSaveActivationCode(savedUser);

        emailService.sendHtmlEmail(user.getEmail(), "[CMD] ACTION_REQUIRED: Verify Identity", "email/verify-account",
                Map.of(
                        "fullName", user.getFullName(),
                        "token", code,
                        "verificationLink", baseUrl + "/auth/verify?token=" + code,
                        "timestamp", Instant.now().toString()
                ));
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        VerificationCode verificationCode = verificationCodeRepository.findByCode(token)
                .orElseThrow(() -> new BusinessLogicException("Invalid verification token"));

        if (verificationCode.getExpiryDate().isBefore(Instant.now())) {
            throw new BusinessLogicException("Verification token expired");
        }

        if (verificationCode.getType() != TokenType.REGISTER) {
            throw new BusinessLogicException("Invalid token type");
        }

        User user = verificationCode.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationCodeRepository.delete(verificationCode);
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessLogicException("User not found")); // Có thể fake return để tránh lộ email

        // Xóa code cũ nếu có
        verificationCodeRepository.findByUserIdAndType(user.getId(), TokenType.RESET_PASSWORD)
                .ifPresent(verificationCodeRepository::delete);

        // Tạo code mới
        String code = generateSixDigitCode();

        VerificationCode vc = VerificationCode.builder()
                .user(user)
                .code(code)
                .type(TokenType.RESET_PASSWORD)
                .expiryDate(Instant.now().plusSeconds(600)) // 10 phút
                .build();
        verificationCodeRepository.save(vc);

        // Gửi mail
        emailService.sendHtmlEmail(user.getEmail(), "[CMD] SECURITY_ALERT: Password Reset", "email/forgot-password",
                Map.of(
                        "email", user.getEmail(),
                        "otpCode", code,
                        "resetLink", baseUrl + "/auth/reset-password?token=" + code,
                        "traceId", UUID.randomUUID().toString()
                ));
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        VerificationCode verificationCode = verificationCodeRepository.findByCode(token)
                .orElseThrow(() -> new BusinessLogicException("Invalid or expired reset token"));

        if (verificationCode.getExpiryDate().isBefore(Instant.now()) || verificationCode.getType() != TokenType.RESET_PASSWORD) {
            throw new BusinessLogicException("Invalid token");
        }

        User user = verificationCode.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        revokeAllUserTokens(user);

        verificationCodeRepository.delete(verificationCode);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (user.isEmailVerified()) {
            throw new BusinessLogicException("Account is already verified. You can login now.");
        }

        // 1. Xóa token cũ (nếu có) để tránh tồn tại song song nhiều token
        verificationCodeRepository.findByUserIdAndType(user.getId(), TokenType.REGISTER)
                .ifPresent(verificationCodeRepository::delete);

        // 2. Tạo token mới
        String code = generateAndSaveActivationCode(user);

        // 3. Gửi lại email
        emailService.sendHtmlEmail(user.getEmail(), "[CMD] ACTION_REQUIRED: Resend Verification Protocol", "email/verify-account",
                Map.of(
                        "fullName", user.getFullName(),
                        "token", code,
                        "verificationLink", baseUrl + "/auth/verify?token=" + code,
                        "timestamp", Instant.now().toString()
                ));
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

    private String generateAndSaveActivationCode(User user) {
        String code = generateSixDigitCode();

        while (verificationCodeRepository.findByCode(code).isPresent()) {
            code = generateSixDigitCode();
        }

        VerificationCode vc = VerificationCode.builder()
                .user(user)
                .code(code)
                .type(TokenType.REGISTER)
                .expiryDate(Instant.now().plusSeconds(900))
                .build();
        verificationCodeRepository.save(vc);
        return code;
    }

    private String generateSixDigitCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
