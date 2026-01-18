# USE CASE SPECIFICATION: MODULE VAULT (CORE ASSET)

**Project:** CareerVault  
**Module:** Core Asset Management Service  
**Version:** 1.0 (Enterprise Ready)  
**Last Updated:** 18/01/2026  
**Tech Stack:** Spring Data JPA, Hibernate, PostgreSQL (Full-text Search), MinIO (Object Storage)

---

## 1. UC-VAULT-01: Quản lý Dự án (Project Asset Lifecycle)

### 1.1. Mô tả tóm tắt

Quản lý vòng đời của một đơn vị công việc (Project). Điểm khác biệt so với CRUD thông thường là việc áp dụng mô hình **S.T.A.R** (Situation - Task - Action - Result) vào cấu trúc dữ liệu để ép người dùng tư duy mạch lạc.

### 1.2. Tác nhân

- **Career Owner (User)**

### 1.3. Luồng sự kiện chính (Basic Flow)

| Bước | Tác nhân                                                          | Hệ thống (Backend)                                                                                                        |
| ---- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| 1    | User tạo mới/Cập nhật Project. Nhập liệu theo các trường S.T.A.R. |                                                                                                                           |
| 2    | Client gửi `POST /api/v1/projects` với payload chi tiết.          |                                                                                                                           |
| 3    |                                                                   | **Validation:** Kiểm tra ràng buộc (Start Date <= End Date, Title không trống).                                           |
| 4    |                                                                   | **Sanitization:** Loại bỏ các thẻ HTML độc hại (XSS prevention) trong phần mô tả.                                         |
| 5    |                                                                   | **Persistence:** Lưu vào Database.                                                                                        |
| 6    |                                                                   | **Search Indexing (Mới 🔧):** Cập nhật trường `search_vector` (PostgreSQL TSVECTOR) để phục vụ tìm kiếm toàn văn sau này. |
| 7    |                                                                   | Trả về Project ID.                                                                                                        |

### 1.4. Quy tắc nghiệp vụ (Business Rules)

#### BR-01: Soft Delete (Xóa mềm)

- **Quy tắc:** Khi User xóa Project, hệ thống **KHÔNG DELETE** dòng đó khỏi Database.
- **Hành động:** Set cột `deleted_at = NOW()`.
- **Lý do:** Để đảm bảo tính toàn vẹn dữ liệu cho các bản Snapshot cũ (Module Time Machine vẫn cần tham chiếu tới Project này).

#### BR-02: Structural Enforcement

Bắt buộc User phải nhập **ít nhất 2/4 trường** của mô hình STAR (Ví dụ: phải có Task và Result) để đảm bảo chất lượng hồ sơ.

---

## 2. UC-VAULT-02: Quản lý Minh chứng (Evidence Handling)

### 2.1. Mô tả tóm tắt

Upload và quản lý các file minh chứng (PDF, Image). Đây là bằng chứng thép để tăng độ tin cậy (Trust) cho hồ sơ.

### 2.2. Tác nhân

- **Career Owner**

### 2.3. Luồng sự kiện chính

| Bước | Tác nhân                                                    | Hệ thống (Backend)                                                                                                                                |
| ---- | ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | User chọn file (PDF báo cáo, Ảnh chụp màn hình).            |                                                                                                                                                   |
| 2    | Client gửi `POST /api/v1/evidences/upload` (MultipartFile). |                                                                                                                                                   |
| 3    |                                                             | **File Validation:** Check đuôi file (.pdf, .png, .jpg) và Magic Number (để tránh file giả mạo). Check kích thước (< 10MB).                       |
| 4    |                                                             | **Storage Handling (MinIO):**<br>1. Rename file thành UUID để tránh trùng tên.<br>2. Stream file lên MinIO/S3.<br>3. Lấy về Public/Presigned URL. |
| 5    |                                                             | **DB Persistence:** Lưu metadata (Tên gốc, URL, File Size, Type) vào bảng `evidences`.                                                            |
| 6    |                                                             | Trả về thông tin Evidence đã lưu.                                                                                                                 |

### 2.4. Yêu cầu kỹ thuật (Technical Constraints)

#### Storage Isolation

File của User nào thì lưu trong Bucket/Folder của User đó (VD: `/user-123/projects/prj-456/report.pdf`).

#### Cleanup Strategy

Nếu User xóa Evidence (Soft delete trong DB), file thật trên MinIO vẫn giữ lại **30 ngày** rồi mới xóa (dùng **MinIO Lifecycle Policy**) để phòng trường hợp khôi phục nhầm.

---

## 3. UC-VAULT-03: Liên kết Kỹ năng (Evidence-Based Skill Mapping)

### 3.1. Mô tả tóm tắt

Đây là phần **"Deep"** của hệ thống. Thay vì User tự chấm điểm "Java: 5/5", User phải liên kết kỹ năng với Project. Hệ thống sẽ tự động tính toán "độ dày" kinh nghiệm.

### 3.2. Luồng sự kiện chính

| Bước | Tác nhân                                                          | Hệ thống (Backend)                                                                                                                                                                                                          |
| ---- | ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | User trong màn hình Project Detail, gõ tag "Java", "Spring Boot". |                                                                                                                                                                                                                             |
| 2    |                                                                   | **Skill Lookup:** Backend tìm xem Skill "Java" đã có trong từ điển chưa.<br>• **Nếu có:** Lấy ID.<br>• **Nếu chưa:** Tạo mới Skill "Java" (nhưng gắn cờ `status = PENDING` để Admin review sau này - xem lại Module Admin). |
| 3    |                                                                   | **Link Creation:** Tạo bản ghi trong bảng trung gian `project_skills`.                                                                                                                                                      |
| 4    |                                                                   | **Level Calculation (Logic ngầm):**<br>• Hệ thống đếm: User đã dùng Java trong bao nhiêu Project?<br>• Nếu > 3 projects → Gợi ý User nâng Level lên "Intermediate".<br>• Nếu > 10 projects → Gợi ý "Expert".                |

### 3.3. Quy tắc nghiệp vụ

#### BR-01: Contextual Skill

Khi link Skill vào Project, User nên ghi chú thêm context (Ví dụ: Link "Java" → Note: "Dùng để viết API Backend"). Điều này giúp CV chi tiết hơn.

---

## 4. UC-VAULT-04: Tìm kiếm & Lọc (Advanced Search)

### 4.1. Mô tả tóm tắt

Giúp User tìm lại "ký ức" của mình. _"Năm ngoái mình dùng thư viện gì để xử lý PDF nhỉ?"_

### 4.2. Luồng sự kiện chính

| Bước | Tác nhân                                  | Hệ thống (Backend)                                                                                             |
| ---- | ----------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| 1    | User gõ từ khóa "PDF" vào thanh tìm kiếm. |                                                                                                                |
| 2    |                                           | **Full-text Search:** Backend thực hiện truy vấn trên trường `search_vector` của bảng Projects và Reflections. |
| 3    |                                           | **Result Ranking:** Ưu tiên hiển thị các Project mới nhất hoặc có chứa từ khóa trong phần "Tech Stack".        |
| 4    |                                           | Trả về danh sách kết quả.                                                                                      |

---

## 5. Yêu cầu Phi chức năng (Vault Module)

### 5.1. Data Integrity (Tính toàn vẹn)

**Transactional:** Việc lưu Project và lưu danh sách Skill đi kèm phải nằm trong cùng 1 Transaction (`@Transactional`). Nếu lưu Skill lỗi, Project cũng không được tạo ra.

### 5.2. Performance (Database Indexing)

- Đánh **Index** cho các cột hay truy vấn: `user_id`, `status`, `start_date`.
- Sử dụng **GIN Index** cho tính năng Search trên PostgreSQL để tốc độ tìm kiếm **< 100ms**.
