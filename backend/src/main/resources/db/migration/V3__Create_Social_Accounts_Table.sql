-- 1. Tạo bảng social_accounts
CREATE TABLE social_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,          -- 'GOOGLE', 'GITHUB', 'GITLAB'
    provider_id VARCHAR(255) NOT NULL,      -- ID định danh từ phía Google/Github gửi về
    email VARCHAR(255),                     -- Email của social account đó (để tham chiếu)
    name VARCHAR(255),                      -- Tên hiển thị trên mạng xã hội đó
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Đảm bảo 1 provider ID chỉ xuất hiện 1 lần (tránh duplicate)
    CONSTRAINT uk_social_provider_id UNIQUE (provider, provider_id)
);

CREATE INDEX idx_social_accounts_user_id ON social_accounts(user_id);

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;