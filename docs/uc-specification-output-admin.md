# USE CASE SPECIFICATION: MODULE OUTPUT & ADMIN

**Project:** CareerVault  
**Module:** Reporting & Administration Service  
**Version:** 1.1 (Enterprise Ready)  
**Last Updated:** 18/01/2026  
**Tech Stack:** Thymeleaf, OpenHTMLtoPDF, PostgreSQL Advisory Locks, Redis

---

## 1. UC-OUT-01: Xuất hồ sơ PDF (Immutable CV Generation)

### 1.1. Mô tả tóm tắt

Chuyển đổi dữ liệu từ Snapshot thành file PDF chuẩn. Điểm nâng cấp là cơ chế **"Ký số" (Digital Fingerprint)** để đảm bảo file PDF tạo ra là duy nhất và có thể truy vết ngược lại phiên bản dữ liệu gốc.

### 1.2. Luồng sự kiện chính (Basic Flow)

| Bước | Tác nhân                            | Hệ thống (Backend)                                                                                |
| ---- | ----------------------------------- | ------------------------------------------------------------------------------------------------- |
| 1    | User chọn Snapshot X và Template Y. |                                                                                                   |
| 2    |                                     | **Validation:** Kiểm tra User có quyền truy cập Snapshot này không.                               |
| 3    |                                     | **Data Fetching:** Lấy `frozen_data` từ bảng `cv_snapshots`.                                      |
| 4    |                                     | **Rendering:** Thymeleaf + OpenHTMLtoPDF render ra file PDF (dạng `byte[]`).                      |
| 5    |                                     | **Integrity Check (Mới 🔧):** Tính toán mã băm SHA-256 Checksum của file PDF vừa tạo.             |
| 6    |                                     | **Metadata Logging (Mới 🔧):** Lưu log xuất file: `{snapshot_id, template, timestamp, checksum}`. |
| 7    |                                     | **Watermarking:** Nhúng metadata (Author, Date, Checksum ID) vào properties ẩn của file PDF.      |
| 8    |                                     | Trả về file PDF cho User.                                                                         |

### 1.3. Quy tắc nghiệp vụ & Kỹ thuật

#### BR-01: Immutable Source

Chỉ xuất từ Snapshot. Tuyệt đối không xuất từ Profile "nháp".

#### BR-02: Digital Integrity (Mới 🔧)

Mỗi file PDF xuất ra phải có một **Checksum duy nhất** lưu trong Database.

**Mục đích:** Nếu sau này User cầm file PDF đi nộp, hệ thống có thể verify xem file đó có bị chỉnh sửa bằng tool bên ngoài (Photoshop/PDF Editor) hay không bằng cách so sánh Checksum.

#### BR-03: Font Embedding

Bắt buộc nhúng font (Roboto/Inter) để hỗ trợ Tiếng Việt trên mọi thiết bị.

#### BR-04: Smart Page Break

Sử dụng CSS `page-break-inside: avoid` cho các khối nội dung quan trọng (Project Card).

---

## 2. UC-ADM-01: Chuẩn hóa & Gộp Kỹ năng (Transactional Skill Merge)

### 2.1. Mô tả tóm tắt

Công cụ vệ sinh dữ liệu (Data Hygiene) giúp Admin gộp các kỹ năng trùng lặp (VD: "ReactJS" → "React"). Quy trình này xử lý vấn đề **Race Condition** (Tranh chấp dữ liệu) bằng cơ chế khóa thông minh.

### 2.2. Luồng sự kiện chính

| Bước | Tác nhân                                                         | Hệ thống (Backend)                                                                                                                                                                                                      |
| ---- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | Admin chọn: Giữ "React" (Target), gộp "ReactJS" (Source) vào đó. |                                                                                                                                                                                                                         |
| 2    | Admin bấm "Merge".                                               | **Concurrency Control (Mới 🔧):** Acquire Advisory Lock trên ID của Target Skill ("React").<br><br>**Mục đích:** Ngăn chặn các tiến trình khác hoặc User khác tác động vào skill này trong lúc đang merge.              |
| 3    |                                                                  | **Transaction Start (ACID):**<br>1. Update bảng `project_skills`: Chuyển tất cả `skill_id` từ Source sang Target.<br>2. Soft Delete bản ghi Source Skill ("ReactJS").<br>3. Ghi Audit Log: "Merged ReactJS into React". |
| 4    |                                                                  | **Transaction Commit.**                                                                                                                                                                                                 |
| 5    |                                                                  | **Release Lock:** Giải phóng khóa.                                                                                                                                                                                      |
| 6    |                                                                  | **Post-Action:** Trigger sự kiện `SKILL_MERGED` để các module khác (VD: AI Search Index) cập nhật lại dữ liệu (Re-index).                                                                                               |

### 2.3. Tác động hệ thống

**Search Consistency:** Đảm bảo khi User tìm kiếm "React", hệ thống sẽ trả về cả những project trước đây được tag là "ReactJS".

---

## 3. UC-ADM-02: Giám sát & Quản lý hạn mức AI (AI Quota Management)

### 3.1. Mô tả tóm tắt

Giám sát việc sử dụng Token AI. Áp dụng cơ chế **Soft-limit** và **Hard-limit** để cân bằng giữa trải nghiệm người dùng và chi phí vận hành.

### 3.2. Luồng sự kiện chính

| Bước | Tác nhân                                          | Hệ thống (Backend)                                                                                                                                                                                                                                                                                                                                   |
| ---- | ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | User thực hiện hành động gọi AI (VD: Analyze JD). |                                                                                                                                                                                                                                                                                                                                                      |
| 2    |                                                   | **Quota Check (Redis):** Kiểm tra số lượng Token đã dùng trong ngày của User.                                                                                                                                                                                                                                                                        |
| 3    |                                                   | **Decision Logic (Mới 🔧):**<br>• **Dưới Soft Limit (80%):** Cho phép đi tiếp.<br>• **Vượt Soft Limit (80-99%):** Cho phép đi tiếp, nhưng trả về Header cảnh báo `X-AI-Quota-Warning: "You are reaching daily limit"`. Frontend sẽ hiện Toast màu vàng nhắc nhở.<br>• **Vượt Hard Limit (100%):** Chặn request. Trả về HTTP `429 Too Many Requests`. |
| 4    |                                                   | Thực hiện gọi AI (nếu được phép).                                                                                                                                                                                                                                                                                                                    |
| 5    |                                                   | **Async Update:** Cập nhật lại số token đã dùng vào Redis và Database (Audit Log).                                                                                                                                                                                                                                                                   |

### 3.3. Quy tắc nghiệp vụ

#### BR-01: Graceful Degradation

Khi chạm Hard Limit, hệ thống không được crash. Nút "Analyze AI" trên Frontend phải chuyển sang trạng thái **Disable** (Xám) và hiện thông báo:

> "Bạn đã dùng hết lượt AI hôm nay. Vui lòng quay lại vào ngày mai."

---

## 4. Yêu cầu Phi chức năng (NFRs)

### 4.1. Performance - PDF Generation Strategy

**Phân tích:** Việc tạo PDF tốn CPU.

#### Quyết định kiến trúc:

- **Với file < 5MB (đa số CV):** Xử lý **Synchronous** (Đồng bộ) để User tải ngay được.
- **Với file > 5MB hoặc export hàng loạt:** Chuyển sang **Asynchronous** (Dùng Spring Event hoặc RabbitMQ), sau đó bắn Notification khi hoàn tất.

**Lý do:** Đảm bảo UX nhanh gọn cho 90% trường hợp, nhưng vẫn an toàn cho hệ thống với 10% trường hợp nặng.

### 4.2. Security - RBAC & Data Scope

**Admin Scope:** Admin có quyền Merge Skill (Metadata), nhưng **KHÔNG** có quyền xem nội dung chi tiết trong CV của User (User Data) trừ khi được User share link. **(Privacy by Design)**
