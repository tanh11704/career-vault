# USE CASE SPECIFICATION: MODULE AUDITOR (INTELLIGENCE SERVICE)

**Project:** CareerVault  
**Module:** Auditor & Intelligence Service  
**Version:** 1.1 (Enterprise Ready)  
**Last Updated:** 18/01/2026  
**Tech Stack:** Spring AI, Gemini Pro / GPT-4o, Redis, PDFBox

---

## 1. UC-AUDIT-01: Phân tích & So khớp Job Description (JD Gap Analysis)

### 1.1. Mô tả tóm tắt

Hệ thống đóng vai trò "Cố vấn kỹ thuật" (Advisor), phân tích JD và so sánh với hồ sơ hiện tại. Điểm đặc biệt là AI không chỉ đưa ra lời khuyên mà còn kèm theo độ tin cậy (Confidence Score) để Backend có thể lọc bớt nhiễu.

### 1.2. Tác nhân

- **Career Owner (User)**
- **AI Engine (System Actor)** - Đóng vai trò Prompt Orchestrator

### 1.3. Luồng sự kiện chính (Basic Flow)

| Bước | Tác nhân                   | Hệ thống (Backend & AI)                                                                                                                    |
| ---- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | User cung cấp JD Text/URL. |                                                                                                                                            |
| 2    |                            | **Context Loading:** Backend lấy Snapshot Skill hiện tại của User.                                                                         |
| 3    |                            | **Prompt Orchestration:** Backend ghép Prompt theo template chuẩn (đã được test kỹ) để tránh User gửi prompt lung tung (Prompt Injection). |
| 4    |                            | **AI Processing:** Gửi request tới LLM.                                                                                                    |
| 5    |                            | **Validation & Filtering:** Backend nhận JSON, validate schema. Lọc bỏ các item có confidence < 0.7.                                       |
| 6    |                            | **Audit Trail:** Lưu kết quả vào `audit_logs` để truy vết sau này.                                                                         |
| 7    |                            | Trả về kết quả phân tích.                                                                                                                  |

### 1.4. Đặc tả dữ liệu (Data Contract Update 🔧)

#### Input Prompt Strategy:

```
Role: Technical Recruiter.
Input: User Skills & JD.
Output: JSON.

Yêu cầu: Với mỗi kỹ năng thiếu, hãy đánh giá mức độ ưu tiên (Priority)
và độ tự tin của bạn (Confidence) dựa trên ngữ cảnh JD.
```

#### Expected Output JSON:

```json
{
  "matchScore": 72,
  "summary": "Bạn phù hợp về Java Core nhưng thiếu các kỹ năng về Cloud Native.",
  "missingSkills": [
    {
      "name": "Kubernetes",
      "priority": "HIGH",
      "confidence": 0.92, // <-- UPDATE: AI tự đánh giá độ chắc chắn
      "reason": "JD yêu cầu kinh nghiệm triển khai Microservices trên K8s."
    },
    {
      "name": "Elasticsearch",
      "priority": "LOW",
      "confidence": 0.65, // <-- Backend có thể ẩn cái này nếu config threshold > 0.7
      "reason": "JD nhắc đến trong phần 'Nice to have'."
    }
  ]
}
```

---

## 2. UC-AUDIT-02: Trích xuất Kỹ năng từ Minh chứng (Evidence Scanner)

### 2.1. Mô tả tóm tắt

Quét tài liệu (PDF/Image) để gợi ý kỹ năng. Tính năng này áp dụng chiến lược **"Human-in-the-loop"**: AI chỉ gợi ý + đưa ra bằng chứng (Evidence Attribution), quyền quyết định cuối cùng thuộc về con người.

### 2.2. Luồng sự kiện chính

| Bước | Tác nhân                                  | Hệ thống (Backend & AI)                                                                                                                |
| ---- | ----------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | User upload file báo cáo (PDF).           |                                                                                                                                        |
| 2    |                                           | **Preprocessing:** PDFBox trích xuất text → Áp dụng thuật toán **Truncation 30-30-30** (Lấy đầu, giữa, cuối) để tối ưu context window. |
| 3    |                                           | **AI Analysis:** Gửi text đã tối ưu lên AI. Prompt yêu cầu trích xuất Skill + Vị trí xuất hiện.                                        |
| 4    |                                           | **Noise Filtering:** Loại bỏ các từ khóa rác ("Introduction", "Conclusion", "MS Word").                                                |
| 5    |                                           | Trả về danh sách gợi ý kèm trích dẫn.                                                                                                  |
| 6    | User xem trích dẫn, tick chọn Skill đúng. | Lưu vào Database.                                                                                                                      |

### 2.3. Đặc tả dữ liệu (Data Contract Update 🔧)

#### Expected Output JSON:

```json
{
  "suggestedSkills": [
    {
      "name": "Spring Security",
      "confidence": 0.88,
      "category": "TECHNICAL",
      "evidenceAttribution": {
        // <-- UPDATE: Tính giải thích (Explainability)
        "source": "Report_Final.pdf",
        "snippet": "...sử dụng bộ lọc JwtAuthFilter để xác thực request..."
      }
    }
  ]
}
```

---

## 3. UC-AUDIT-03: Viết lại nội dung (Smart Rewriter)

### 3.1. Mô tả tóm tắt

Viết lại mô tả dự án theo chuẩn S.T.A.R. AI đóng vai trò biên tập viên (Editor).

### 3.2. Quy tắc nghiệp vụ (Business Rules Update 🔧)

#### BR-01: Hallucination Prevention (Metric Awareness)

**Quy tắc:** Nếu input của User chứa con số cụ thể (VD: "giảm 50% latency", "xử lý 1000 users"), output của AI **BẮT BUỘC** phải giữ nguyên con số đó. Không được tự bịa ra số liệu mới để làm đẹp CV.

#### BR-02: Tone Consistency

Luôn giữ giọng văn chuyên nghiệp (Formal), không dùng từ ngữ cảm thán hoặc ngôi thứ nhất quá nhiều.

**Ví dụ:**

- **Input:** "Tôi làm cái api chạy nhanh hơn 20%."
- **Output (OK):** "Tối ưu hóa hiệu năng API, cải thiện tốc độ xử lý thêm 20% thông qua việc tinh chỉnh câu lệnh SQL."
- **Output (Fail - Bịa số):** "Tối ưu hóa API giúp giảm tải server 50%." → Hệ thống cần cảnh báo hoặc reject.

---

## 4. Yêu cầu Phi chức năng & Kỹ thuật (NFRs - Enterprise Grade)

### 4.1. Asynchronous Processing (Cập nhật 🔧)

Để tránh block thread của Tomcat (Web Server) khi gọi AI (vốn có latency cao ~5-10s):

**❌ CẤM:**

- Dùng `@Async` đơn thuần trên Thread Pool mặc định của Spring (dễ gây cạn kiệt thread).

**✅ KHUYÊN DÙNG:**

- Sử dụng **Spring WebClient** (Reactive Stack) để gọi External API của AI.
- Hoặc sử dụng `CompletableFuture` với một `ThreadPoolTaskExecutor` được cấu hình riêng (VD: `ai-executor-pool`).
- **Đối với UX:** Sử dụng **Server-Sent Events (SSE)** để stream từng token kết quả về Frontend, giảm cảm giác chờ đợi.

### 4.2. Caching Strategy (Cập nhật 🔧)

Cơ chế Cache cần thông minh để tránh dữ liệu cũ (Stale Data).

#### Cache Key Formula:

```java
String cacheKey = Hash(JdContent + UserProfileVersion + AlgorithmVersion);
```

**Giải thích:**

- **JdContent:** Nội dung JD.
- **UserProfileVersion:** Nếu User vừa update skill mới, cache cũ phải vô hiệu → Cần hash cả version của profile vào key.
- **AlgorithmVersion:** Nếu Dev update prompt mới, cache cũ cũng phải vô hiệu.

**TTL (Time-to-live):** 24 giờ.

### 4.3. Resilience & Fallback

#### Rate Limiting

Cấu hình **Resilience4j Ratelimiter** để giới hạn mỗi User chỉ được gọi AI tối đa **10 lần/giờ** (tránh spam tốn tiền).

#### Fallback

Nếu Gemini Pro bị lỗi (5xx), hệ thống tự động switch sang **OpenAI GPT-3.5** hoặc trả về thông báo lỗi thân thiện.
