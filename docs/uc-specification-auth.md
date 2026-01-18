# USE CASE SPECIFICATION: MODULE AUTHENTICATION

**Project:** CareerVault  
**Module:** Authentication & Security Service  
**Version:** 1.0  
**Last Updated:** 18/01/2026

---

## 1. UC-AUTH-01: Đăng nhập hệ thống (Standard Login)

### 1.1. Mô tả tóm tắt (Brief Description)

Cho phép người dùng (Guest) truy cập vào hệ thống bằng Email và Mật khẩu đã đăng ký. Nếu thông tin hợp lệ, hệ thống cấp phát cặp thẻ bài bảo mật (Access Token & Refresh Token).

### 1.2. Tác nhân (Actors)

- **Guest (Khách):** Người dùng chưa đăng nhập.

### 1.3. Tiền điều kiện (Pre-conditions)

- Người dùng đã đăng ký tài khoản và đã xác thực Email (Email Verified = True).
- Tài khoản không bị khóa (Active = True).

### 1.4. Hậu điều kiện (Post-conditions)

- **Thành công:** Client nhận được cặp JWT (Access Token & Refresh Token). Trạng thái `last_login_at` trong DB được cập nhật.
- **Thất bại:** Hệ thống trả về thông báo lỗi, client không nhận được Token.

### 1.5. Luồng sự kiện chính (Basic Flow / Main Success Scenario)

| Bước | Tác nhân (Client/User)                                                      | Hệ thống (System/Backend)                                                                                 |
| ---- | --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| 1    | User nhập Email và Password, nhấn "Login".                                  |                                                                                                           |
| 2    | Client gửi request `POST /api/v1/auth/login` với payload (email, password). |                                                                                                           |
| 3    |                                                                             | **Validate Input:** Kiểm tra định dạng email và độ dài password.                                          |
| 4    |                                                                             | **Query User:** Tìm kiếm User trong Database theo Email.                                                  |
| 5    |                                                                             | **Verify Password:** Sử dụng thuật toán BCrypt để so khớp mật khẩu gửi lên với `password_hash` trong DB.  |
| 6    |                                                                             | **Generate Token:** Tạo Access Token (chứa claims: userId, role, email) và Refresh Token.                 |
| 7    |                                                                             | **Store Refresh Token:** Lưu Refresh Token (dưới dạng Hash) vào DB hoặc Redis để quản lý thu hồi sau này. |
| 8    |                                                                             | Trả về Response `200 OK` kèm cặp Token.                                                                   |

### 1.6. Luồng thay thế / Ngoại lệ (Alternate / Exception Flows)

#### E1: Sai Email hoặc Mật khẩu

- **Tại bước 4 hoặc 5:** Nếu User không tồn tại hoặc Hash không khớp.
- Hệ thống trả về **HTTP 401 Unauthorized**.
- Thông báo chung chung: _"Invalid credentials"_ (Không báo chi tiết sai email hay sai pass để chống dò quét user).

#### E2: Tài khoản chưa xác thực Email

- **Tại bước 4:** User tồn tại nhưng `is_verified = false`.
- Hệ thống trả về **HTTP 403 Forbidden**.
- Thông báo: _"Please verify your email first"_.

#### E3: Tài khoản bị khóa (Locked)

- **Tại bước 4:** User tồn tại nhưng `status = LOCKED`.
- Hệ thống trả về **HTTP 403 Forbidden**.
- Thông báo: _"Account is locked due to suspicious activity"_.

---

## 2. UC-AUTH-02: Đăng nhập bằng Google (OAuth2)

### 2.1. Mô tả tóm tắt

Cho phép người dùng đăng nhập/đăng ký nhanh thông qua tài khoản Google. Hệ thống đóng vai trò là OAuth2 Client.

### 2.2. Tác nhân

- **Guest**
- **Google Identity Platform** (System Actor)

### 2.3. Luồng sự kiện chính (Basic Flow)

| Bước | Tác nhân (Client/User)                                                         | Hệ thống (System/Backend)                                                                                                                                                      |
| ---- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | User nhấn nút "Login with Google".                                             |                                                                                                                                                                                |
| 2    | Client chuyển hướng (Redirect) User sang trang xác thực của Google.            |                                                                                                                                                                                |
| 3    | User đăng nhập và cấp quyền cho CareerVault.                                   | **Google System:** Xác thực User và trả về Authorization Code qua Redirect URL.                                                                                                |
| 4    | Client nhận Code, gửi request `POST /api/v1/auth/google` kèm Code lên Backend. |                                                                                                                                                                                |
| 5    |                                                                                | **Exchange Token:** Backend gửi Code + Client Secret sang Google để lấy Google Access Token.                                                                                   |
| 6    |                                                                                | **Fetch User Info:** Dùng Google Access Token gọi Google API để lấy Profile (Email, Name, Avatar).                                                                             |
| 7    |                                                                                | **User Sync Logic:**<br>• TH1 (Đã có User): Cập nhật Avatar/Name nếu có thay đổi.<br>• TH2 (Chưa có User): Tự động tạo User mới với `provider = GOOGLE`, `is_verified = true`. |
| 8    |                                                                                | **Generate Token:** Tạo cặp JWT nội bộ của CareerVault (giống UC-01).                                                                                                          |
| 9    |                                                                                | Trả về Response `200 OK` kèm cặp Token.                                                                                                                                        |

### 2.4. Quy tắc nghiệp vụ đặc thù (Business Rules)

**BR-01 (Merge Account):** Nếu User đã có tài khoản đăng ký bằng Email/Password, sau đó Login bằng Google với cùng Email đó, hệ thống phải tự động liên kết (Link) tài khoản Google vào User cũ, không tạo User mới.

---

## 3. UC-AUTH-03: Cấp lại Token (Refresh Access Token)

### 3.1. Mô tả tóm tắt

Cấp phát Access Token mới khi token cũ hết hạn mà không yêu cầu người dùng đăng nhập lại. Đây là tính năng cốt lõi cho trải nghiệm "Stay Logged In".

### 3.2. Tiền điều kiện

- Client đang nắm giữ Refresh Token hợp lệ (chưa hết hạn, chưa bị thu hồi).

### 3.3. Luồng sự kiện chính (Basic Flow)

| Bước | Tác nhân (Client/User)                                                         | Hệ thống (System/Backend)                                                                                                            |
| ---- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | Client phát hiện Access Token sắp hết hạn hoặc nhận lỗi 401 khi gọi API.       |                                                                                                                                      |
| 2    | Client gửi request `POST /api/v1/auth/refresh` với payload chứa Refresh Token. |                                                                                                                                      |
| 3    |                                                                                | **Validate Token:** Kiểm tra chữ ký (Signature) và thời hạn (Expiration) của Refresh Token.                                          |
| 4    |                                                                                | **Check Revocation:** Kiểm tra trong Database/Redis xem Token này có nằm trong "Blacklist" hoặc đã bị User đăng xuất chưa.           |
| 5    |                                                                                | **Check User Status:** Đảm bảo User sở hữu token này chưa bị khóa/xóa.                                                               |
| 6    |                                                                                | **Rotate Token (Optional):** Tạo Access Token mới. Có thể tạo luôn Refresh Token mới và hủy cái cũ (Token Rotation) để tăng bảo mật. |
| 7    |                                                                                | Trả về Response `200 OK` kèm Token mới.                                                                                              |

### 3.4. Luồng ngoại lệ

#### E1: Refresh Token hết hạn hoặc không hợp lệ

- Hệ thống trả về **HTTP 401**.
- Client bắt buộc phải xóa Token lưu trữ và điều hướng User về trang Login (Force Logout).

---

## 4. Yêu cầu Phi chức năng & Bảo mật (Non-Functional Requirements)

### 4.1. Bảo mật (Security Constraints)

- **Transport Security:** Tất cả các request Auth phải đi qua HTTPS (TLS 1.2+).
- **Password Storage:** Mật khẩu KHÔNG BAO GIỜ được lưu dạng rõ (Plain text). Bắt buộc dùng BCrypt (với strength >= 10).
- **Token Storage:**
  - **Backend:** Không lưu Access Token. Chỉ lưu hash của Refresh Token (nếu cần thu hồi).
  - **Frontend:** Khuyến nghị lưu Access Token trong Memory (Biến JS), Refresh Token trong HttpOnly Cookie để chống XSS.
- **JWT Signing:** Sử dụng thuật toán RS256 (Asymmetric Key) hoặc HS256 (Symmetric Key) với Secret Key tối thiểu 256-bit.

### 4.2. Hiệu năng (Performance)

- **Latency:** API Login và Refresh Token phải phản hồi < 200ms (95th percentile).
- **Concurrency:** Module Auth phải chịu tải cao. Khuyến nghị sử dụng Redis để cache User Profile và Blacklist Token.

### 4.3. Chính sách mật khẩu (Password Policy)

- Tối thiểu 8 ký tự.
- Bao gồm ít nhất: 1 chữ hoa, 1 chữ thường, 1 số, 1 ký tự đặc biệt.
