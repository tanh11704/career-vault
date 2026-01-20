package com.tpanh.server.modules.auth.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tpanh.server.common.exception.BusinessLogicException;
import com.tpanh.server.common.exception.ResourceNotFoundException;
import com.tpanh.server.common.service.EmailService;
import com.tpanh.server.modules.auth.dto.AuthResponse;
import com.tpanh.server.modules.auth.dto.GoogleLoginRequest;
import com.tpanh.server.modules.auth.dto.LoginRequest;
import com.tpanh.server.modules.auth.dto.RegisterRequest;
import com.tpanh.server.modules.auth.entity.RefreshToken;
import com.tpanh.server.modules.auth.entity.SocialAccount;
import com.tpanh.server.modules.auth.entity.User;
import com.tpanh.server.modules.auth.entity.VerificationCode;
import com.tpanh.server.modules.auth.enums.AuthProvider;
import com.tpanh.server.modules.auth.enums.Role;
import com.tpanh.server.modules.auth.enums.TokenType;
import com.tpanh.server.modules.auth.mapper.AuthMapper;
import com.tpanh.server.modules.auth.repository.RefreshTokenRepository;
import com.tpanh.server.modules.auth.repository.SocialAccountRepository;
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuthMapper authMapper;
    private final EmailService emailService;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    @Value("${application.api.base-url}")
    private String baseUrl;

    @Value("${application.security.google.client-id}")
    private String googleClientId;

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

        if (user.getPasswordHash() == null) {
            // User này thuần Social -> Gửi mail nhắc nhở thay vì OTP
            emailService.sendHtmlEmail(user.getEmail(),
                    "[CareerVault] Thông báo đăng nhập",
                    "email/social-login-notice", // Template mới
                    Map.of("fullName", user.getFullName())
            );
            return; // Dừng tại đây
        }

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
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        // 1. Verify ID Token với Google (như code cũ)
        GoogleIdToken.Payload payload = verifyGoogleToken(request.idToken()); // Hàm verify tách riêng cho gọn

        String providerId = payload.getSubject(); // ID định danh của Google
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        // 2. Gọi hàm xử lý chung (Helper)
        return processSocialLogin(providerId, email, name, pictureUrl, AuthProvider.GOOGLE);
    }

    @Override
    @Transactional
    public void createPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessLogicException("User not found"));

        // Bảo mật: Nếu user đã có password rồi thì KHÔNG ĐƯỢC dùng API này
        if (user.getPasswordHash() != null) {
            throw new BusinessLogicException("User already has a password. Please use 'Change Password' feature.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
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

    // Hàm xử lý chung cho Social Login
    private AuthResponse processSocialLogin(String providerId, String email, String name, String avatarUrl, AuthProvider provider) {

        // 1. Check xem Social Account này đã tồn tại chưa
        Optional<SocialAccount> socialAccountOpt = socialAccountRepository.findByProviderAndProviderId(provider, providerId);

        User user;
        if (socialAccountOpt.isPresent()) {
            // Case A: Đã từng login bằng tài khoản Social này -> Lấy User ra
            user = socialAccountOpt.get().getUser();
            updateSocialInfo(socialAccountOpt.get(), email, name);
        } else {
            // Case B: Chưa từng login Social này -> Check xem email có tồn tại trong hệ thống chưa
            Optional<User> existingUserOpt = userRepository.findByEmail(email);

            if (existingUserOpt.isPresent()) {
                // Case B1: Email đã tồn tại (User Local hoặc Google cũ) -> LINK TÀI KHOẢN
                user = existingUserOpt.get();
            } else {
                // Case B2: User hoàn toàn mới -> TẠO USER MỚI
                user = User.builder()
                        .email(email)
                        .fullName(name)
                        .avatarUrl(avatarUrl)
                        .role(Role.USER)
                        .isActive(true)
                        .isEmailVerified(true) // Social auto verified
                        .passwordHash(null)
                        .build();
                user = userRepository.save(user);
            }

            // Tạo liên kết Social Account mới
            SocialAccount newSocialAccount = SocialAccount.builder()
                    .user(user)
                    .provider(provider)
                    .providerId(providerId)
                    .email(email)
                    .name(name)
                    .build();
            socialAccountRepository.save(newSocialAccount);
        }

        // Tạo Token trả về
        revokeAllUserTokens(user);
        var jwtToken = jwtService.generateToken(new CustomUserDetails(user));
        var refreshToken = generateAndSaveRefreshToken(user);
        return authMapper.toAuthResponse(user, jwtToken, refreshToken);
    }

    private void updateSocialInfo(SocialAccount socialAccount, String email, String name) {
        // Logic update nếu cần
        if (email != null) socialAccount.setEmail(email);
        if (name != null) socialAccount.setName(name);
        socialAccountRepository.save(socialAccount);
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            // Tạo đối tượng Verifier
            // NetHttpTransport: Dùng để gọi API check key của Google
            // GsonFactory: Dùng để parse JSON response
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory()
            )
                    // Chỉ chấp nhận Token được cấp cho Client ID của mình
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            // Thực hiện verify
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken != null) {
                // Token hợp lệ -> Trả về Payload chứa info (email, name, avatar...)
                return idToken.getPayload();
            } else {
                // Token không hợp lệ (hết hạn, sai chữ ký...)
                throw new BusinessLogicException("Invalid Google ID Token.");
            }

        } catch (GeneralSecurityException | IOException e) {
            // Lỗi kỹ thuật hoặc lỗi bảo mật khi verify
            throw new BusinessLogicException("Google Authentication Failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Lỗi format token bị sai
            throw new BusinessLogicException("Malformed Google Token.");
        }
    }
}
