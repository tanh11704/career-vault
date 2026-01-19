-- Tạo bảng lưu mã xác thực
CREATE TABLE verification_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code VARCHAR(255) NOT NULL,          -- Mã token (UUID string)
    type VARCHAR(50) NOT NULL,           -- 'REGISTER' hoặc 'RESET_PASSWORD'
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_verification_codes_code ON verification_codes(code);