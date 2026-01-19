package com.tpanh.server.common.scheduler;

import com.tpanh.server.modules.auth.repository.RefreshTokenRepository;
import com.tpanh.server.modules.auth.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataCleanupScheduler {

    private final VerificationCodeRepository verificationCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Task 1: Dọn dẹp OTP hết hạn
     * Chạy lúc 03:00 AM mỗi ngày (Giờ VN)
     * Cron: giây phút giờ ngày tháng thứ
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanupExpiredVerificationCodes() {
        log.info("🧹 Start cleaning up expired verification codes...");

        try {
            // Xóa tất cả code có expiryDate < giờ hiện tại
            verificationCodeRepository.deleteByExpiryDateBefore(Instant.now());
            log.info("✅ Expired verification codes cleaned successfully.");
        } catch (Exception e) {
            log.error("❌ Failed to clean verification codes", e);
        }
    }

    /**
     * Task 2: Dọn dẹp Refresh Token rác
     * Chạy lúc 04:00 AM ngày mùng 1 hàng tháng (Giờ VN)
     */
    @Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        log.info("🧹 Start cleaning up expired/revoked refresh tokens...");

        try {
            // Xóa token hết hạn hoặc đã bị revoke
            refreshTokenRepository.deleteByExpiryDateBeforeOrRevokedTrue(Instant.now());
            log.info("✅ Refresh tokens cleaned successfully.");
        } catch (Exception e) {
            log.error("❌ Failed to clean refresh tokens", e);
        }
    }
}
