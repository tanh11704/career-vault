# USE CASE SPECIFICATION: MODULE TIME MACHINE (VERSIONING SERVICE)

**Project:** CareerVault  
**Module:** History & Version Control Service  
**Version:** 1.0 (Enterprise Ready)  
**Last Updated:** 18/01/2026  
**Tech Stack:** PostgreSQL JSONB, Javers (Object Diff), Spring Data JPA

---

## 1. UC-TIME-01: Tạo phiên bản CV (Create CV Snapshot)

### 1.1. Mô tả tóm tắt

Đóng băng (Freeze) toàn bộ trạng thái hồ sơ năng lực của người dùng tại một thời điểm cụ thể. Bản Snapshot này là **Bất biến (Immutable)** – không thể chỉnh sửa sau khi tạo.

### 1.2. Tác nhân

- **Career Owner (User)**

### 1.3. Tiền điều kiện

- User có dữ liệu Profile/Project để sao lưu.

### 1.4. Luồng sự kiện chính (Basic Flow)

| Bước | Tác nhân                                                       | Hệ thống (Backend)                                                                                                                                |
| ---- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | User bấm "Create Version" (Ví dụ trước khi apply vào Viettel). |                                                                                                                                                   |
| 2    | Client gửi `POST /api/v1/snapshots` (payload: tên phiên bản).  |                                                                                                                                                   |
| 3    |                                                                | **Deep Fetching:** Backend truy vấn (Eager Load) toàn bộ Object Graph:<br>`User -> Projects -> Skills, User -> Educations, User -> Certificates`. |
| 4    |                                                                | **Serialization:** Chuyển đổi toàn bộ Object Graph trên thành một chuỗi JSON khổng lồ.                                                            |
| 5    |                                                                | **Persist (JSONB):** Lưu JSON đó vào cột `frozen_data` (Kiểu `jsonb` của PostgreSQL) trong bảng `cv_snapshots`.                                   |
| 6    |                                                                | **Metadata:** Ghi lại `created_at`, `version_name`.                                                                                               |
| 7    |                                                                | Trả về ID của Snapshot mới tạo.                                                                                                                   |

### 1.5. Quy tắc nghiệp vụ (Business Rules)

#### BR-01: Immutability (Tính bất biến)

Một khi Snapshot đã tạo, User **không thể sửa** nội dung bên trong. Nếu muốn sửa, User phải quay ra sửa Profile gốc và tạo Snapshot mới.

#### BR-02: Storage Optimization

Chỉ lưu những dữ liệu có giá trị hiển thị trên CV. Không lưu các trường rác hoặc metadata nội bộ không cần thiết để tiết kiệm dung lượng DB.

---

## 2. UC-TIME-02: So sánh phiên bản (Version Diffing)

### 2.1. Mô tả tóm tắt

Tính năng **"Wow"** của hệ thống. Cho phép User chọn 2 phiên bản (Ví dụ: "CV Năm 3" và "CV Năm 4") để xem sự tiến bộ của mình. Hệ thống sẽ highlight những phần **Thêm mới (Green)**, **Xóa bỏ (Red)**, hoặc **Chỉnh sửa (Yellow)**.

### 2.2. Tác nhân

- **Career Owner**

### 2.3. Luồng sự kiện chính

| Bước | Tác nhân                                                    | Hệ thống (Backend)                                                                                                                                                                                  |
| ---- | ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | User chọn Snapshot A (Gốc) và Snapshot B (Đích) để so sánh. |                                                                                                                                                                                                     |
| 2    |                                                             | **Fetch Data:** Backend lấy 2 cục JSON từ Database.                                                                                                                                                 |
| 3    |                                                             | **Diff Algorithm:** Sử dụng thư viện **Javers** hoặc **JsonDiff** để so sánh cấu trúc 2 file JSON.                                                                                                  |
| 4    |                                                             | **Compute Changes:**<br>• **New Projects:** Có trong B mà không có trong A.<br>• **Skill Level Up:** Skill "Java" ở A là Level 2, ở B là Level 3.<br>• **Removed Items:** Có trong A nhưng mất ở B. |
| 5    |                                                             | Trả về kết quả Diff dạng cấu trúc (Structured Diff) cho Frontend render màu sắc.                                                                                                                    |

### 2.4. Đặc tả dữ liệu Output (Diff Result)

```json
{
  "changes": [
    {
      "type": "SKILL_LEVEL_CHANGE",
      "entity": "Java",
      "oldValue": "Intermediate",
      "newValue": "Advanced"
    },
    {
      "type": "PROJECT_ADDED",
      "entity": "CareerVault System",
      "value": {
        "role": "Backend Lead",
        "tech": "Spring Boot"
      }
    }
  ]
}
```

---

## 3. UC-TIME-03: Nhật ký hoạt động (Audit Trail)

### 3.1. Mô tả tóm tắt

Ghi lại mọi tác động quan trọng lên dữ liệu (Ai làm gì, lúc nào, thay đổi cái gì). Đây là yêu cầu bắt buộc của các hệ thống Enterprise.

### 3.2. Tác nhân

- **System (Automatic)** - User không trực tiếp gọi chức năng này.

### 3.3. Luồng sự kiện chính (Implicit Flow)

| Bước | Sự kiện                                             | Hệ thống (Backend)                                                                                          |
| ---- | --------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| 1    | User thực hiện hành động Cập nhật Project (Update). |                                                                                                             |
| 2    |                                                     | **Entity Listener (@PostUpdate):** Spring Data JPA chặn sự kiện ngay sau khi commit transaction thành công. |
| 3    |                                                     | **Capture Change:** So sánh trạng thái cũ và mới của Entity.                                                |
| 4    |                                                     | **Async Log:** Đẩy một event vào luồng ghi log (bất đồng bộ) để không làm chậm request chính.               |
| 5    |                                                     | Lưu vào bảng `audit_logs`:<br>`User X updated Project Y. Changed 'Status' from 'In Progress' to 'Done'.`    |

### 3.4. Quy tắc kỹ thuật

#### Non-blocking

Việc ghi log thất bại **KHÔNG ĐƯỢC** làm rollback giao dịch chính (Project vẫn phải được update dù log lỗi).

#### Retention Policy

Log chỉ lưu trữ trong vòng **12 tháng** (có thể cấu hình xóa định kỳ bằng Spring Batch).

---

## 4. Yêu cầu Phi chức năng (NFRs)

### 4.1. Storage Efficiency (Hiệu quả lưu trữ)

#### Vấn đề

Nếu User tạo 100 snapshot, mỗi snapshot nặng 100KB → Database sẽ phình to rất nhanh.

#### Giải pháp

Sử dụng **PostgreSQL TOAST** (The Oversized-Attribute Storage Technique) - cơ chế nén tự động của Postgres cho các cột JSONB lớn.

> **💡 Tip:** Trong đồ án, bạn chỉ cần cấu hình compression cho cột JSONB là đủ "ăn điểm".

### 4.2. Performance (Diffing)

Việc so sánh 2 JSON lớn tốn CPU.

#### Chiến lược

Thực hiện tính toán Diff ở phía **Backend (Java)** vì thư viện **Javers** của Java mạnh hơn JS.

**❌ Tránh:** Gửi 2 cục JSON to đùng về Frontend bắt JS xử lý (gây lag trình duyệt).
