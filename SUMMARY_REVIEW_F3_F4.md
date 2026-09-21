# BẢNG RÀ SOÁT, ĐỐI CHỨNG CÁC API FRAME 03 VÀ FRAME 04
**Dự án:** eGuarantee Lite  
**Tài liệu đối chứng:** `Frontend_eGuarantee_Lite_Assignment.docx`  
**Mã nguồn đối chứng:** Package `com.example.ecommerce` (`GuaranteeController`, `GuaranteeService`, DTOs, SecurityConfig, Liquibase changelog)  
**Ngày lập:** 21/09/2026  

---

## I. TỔNG QUAN PHẠM VI FRAME 03 VÀ FRAME 04

| Frame | Tên màn hình / Chức năng | Vai trò chính | Các API tương tác |
| :--- | :--- | :--- | :--- |
| **Frame 03** | **Detail / Maker View**<br>(Xem chi tiết & thao tác của Maker) | `MAKER` (BE: `MARKER`) | - `GET /api/v1/guarantees/{id}`<br>- `GET /api/v1/guarantees/{id}/histories`<br>- `PUT /api/v1/guarantees/{id}` (Edit)<br>- `DELETE /api/v1/guarantees/{id}` (Xóa DRAFT)<br>- `POST /api/v1/guarantees/{id}/submit` (Gửi duyệt) |
| **Frame 04** | **Detail / Checker View + Modal**<br>(Xem chi tiết & phê duyệt/từ chối) | `CHECKER` | - `GET /api/v1/guarantees/{id}`<br>- `GET /api/v1/guarantees/{id}/histories`<br>- `POST /api/v1/guarantees/{id}/approve` (Phê duyệt)<br>- `POST /api/v1/guarantees/{id}/reject` (Từ chối kèm reason) |

---

## II. MA TRẬN ĐỐI CHỨNG CHI TIẾT TỪNG API

### 1. `GET /api/v1/guarantees/{id}` (Xem chi tiết hồ sơ - F3 & F4)

| Hạng mục đối chứng | Yêu cầu trong File Assignment | Hiện trạng Backend (`eGuarantee`) | Đánh giá & Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **Endpoint & Method** | `GET /api/v1/guarantees/:id` | `GET /api/v1/guarantees/{id}` | **Khớp** |
| **Phân quyền** | Maker & Checker đều được xem | `hasAnyRole("MARKER", "CHECKER")` | **Khớp** (Lưu ý tên role `MARKER`) |
| **Thông tin hồ sơ** | - Khách hàng (CIF, Name, TaxCode, Address)<br>- Bảo lãnh (Type, Amount, Currency, EffectiveDate, ExpiryDate, GuaranteeDays, TenderNumber, Purpose...)<br>- Thụ hưởng (Name, Address)<br>- Liên hệ (Email, Phone)<br>- Trạng thái (Status) | `GuaranteeResponse` chứa đầy đủ các trường tương ứng, map từ `GuaranteeRequest` & `Customer`. | **Khớp** |
| **Guarantee Days** | Tự tính (Read-only: Expiry Date - Effective Date) | `getGuaranteeDays()` tự tính bằng `ChronoUnit.DAYS.between(effectiveDate, expiryDate)`. | **Khớp** |
| **Thông tin Audit** | `createdBy`, `createdDate`, `updatedBy`, `updatedDate` | Có đủ trong `GuaranteeResponse` (kèm cả `createdByFullName`, `updatedByFullName`). | **Khớp** |
| **Cấu trúc Response** | Mock API hợp đồng trả trực tiếp data object | Bọc trong `ApiResponse<GuaranteeResponse>`: `{ success, code, message, data, timestamp }`. | **Lưu ý FE**: Bóc tách dữ liệu từ thuộc tính `data`. |

---

### 2. `GET /api/v1/guarantees/{id}/histories` (Lịch sử xử lý - F3 & F4)

| Hạng mục đối chứng | Yêu cầu trong File Assignment | Hiện trạng Backend (`eGuarantee`) | Đánh giá & Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **Endpoint & Method** | `GET /api/v1/guarantees/:id/histories` | `GET /api/v1/guarantees/{id}/histories` | **Khớp** |
| **Các trường dữ liệu** | Action, User, Role, Timestamp, Comment (nếu có) | `ProcessingHistoryResponse`: `id`, `guaranteeId`, `action`, `performedBy`, `performedByFullName`, `role`, `timestamp`, `comment`. | **Khớp** |
| **Các hành động ghi nhận** | `CREATE`, `UPDATE`, `SUBMIT`, `APPROVE`, `REJECT` | Enum `Action` có đủ 5 actions và đều được gọi ghi nhận trong service. | **Khớp** |
| **Thứ tự sắp xếp** | Hiển thị Timeline theo tiến trình thời gian | Đang gọi `findByGuaranteeRequest_IdOrderByTimestampDesc` (sắp xếp giảm dần - mới nhất lên đầu). | **Khuyến nghị**: Timeline giao diện thường chạy từ cũ đến mới (`Asc`), hoặc FE cần reverse mảng trước khi vẽ Timeline. |

---

### 3. `PUT /api/v1/guarantees/{id}` (Chỉnh sửa hồ sơ - F3 Maker Action)

| Hạng mục đối chứng | Yêu cầu trong File Assignment | Hiện trạng Backend (`eGuarantee`) | Đánh giá & Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **Endpoint & Method** | `PUT /api/v1/guarantees/:id` | `PUT /api/v1/guarantees/{id}` | **Khớp** |
| **Phân quyền** | Chỉ Maker (BR-06, BR-09) | `SecurityConfiguration`: `.hasRole("MARKER")`. | **Khớp** |
| **Trạng thái cho phép sửa** | Chỉ `DRAFT` hoặc `REJECTED` (BR-06) | Kiểm tra `status != DRAFT && status != REJECTED -> throw BadRequestException`. | **Khớp** |
| **Chuyển trạng thái khi sửa hồ sơ REJECTED** | "Sau khi Maker chỉnh sửa REJECTED và lưu, hồ sơ trở lại DRAFT" (BR-11, Mục 6.4) | Code xử lý: `if (existing.getStatus() == REJECTED) { existing.setStatus(DRAFT); }` | **Khớp tuyệt đối** |
| **Validate Phone Number** | **Không bắt buộc**; 9-15 chữ số: `^[0-9]{9,15}$` (Mục 6.3) | `@Pattern(regexp = "^[0-9]{10}$")`: **Bắt buộc đúng 10 chữ số** | **LỆCH**: BE bắt chẽ hơn đề bài (chỉ cho 10 số). Đề bài cho phép 9 đến 15 số. |
| **Validate Effective Date** | Bắt buộc, ngày hiệu lực | Có thêm `@FutureOrPresent` | **Lưu ý**: Nếu hồ sơ cũ tạo từ trước có effectiveDate là hôm qua thì khi update sẽ bị chặn. |
| **Validate CIF Khách hàng** | 6-12 chữ số `^[0-9]{6,12}$` | Validate qua `@Pattern` và kiểm tra tồn tại trong bảng `Customer`. Tên và TaxCode phải khớp DB. | **Lưu ý**: Khác với mock-data tự do, BE thật bắt buộc CIF phải có sẵn trong DB. |
| **Currency** | Bắt buộc VND/USD, mặc định VND | Trong `GuaranteeUpdateRequest` chưa có `@NotNull`, không tự fallback VND nếu client gửi null. | Cần lưu ý FE luôn gửi `currency`. |

---

### 4. `DELETE /api/v1/guarantees/{id}` (Xóa hồ sơ DRAFT - F3 Maker Action)

| Hạng mục đối chứng | Yêu cầu trong File Assignment | Hiện trạng Backend (`eGuarantee`) | Đánh giá & Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **Endpoint & Method** | `DELETE /api/v1/guarantees/:id` (Mục 11 Contract) | **CHƯA CÓ TRONG CONTROLLER** | **CHƯA TRIỂN KHAI (MISSING)** |
| **Nghiệp vụ BR-05** | "Chỉ DRAFT được Delete" | Chưa có hàm xử lý trong `GuaranteeService` | **THIẾU** |
| **Nghiệp vụ BR-09** | "Checker không được Delete" | Chưa cấu hình route trong `SecurityConfiguration` | **THIẾU** |
| **Giao diện F3** | Frame 03 quy định action cho status DRAFT: `Edit, Delete, Submit` (kèm modal confirm) | Khi nhấn nút Delete ở Frame 03, FE sẽ bị lỗi 404/405 do BE chưa có API này. | **CẦN BỔ SUNG NGAY** |

---

### 5. `POST /api/v1/guarantees/{id}/submit` (Gửi phê duyệt - F3 Maker Action)

| Hạng mục đối chứng | Yêu cầu trong File Assignment | Hiện trạng Backend (`eGuarantee`) | Đánh giá & Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **Endpoint & Method** | `POST /api/v1/guarantees/:id/submit` | `POST /api/v1/guarantees/{id}/submit` | **Khớp** |
| **Phân quyền** | Chỉ Maker được gửi (BR-08, BR-09) | Security: `hasRole("MARKER")`, Service: `currentUser.getRole() != Role.MARKER`. | **Khớp** |
| **Trạng thái cho phép submit** | Chỉ `DRAFT` (hoặc sau khi sửa REJECTED đã chuyển về DRAFT) | `if (guaranteeRequest.getStatus() != GuaranteeStatus.DRAFT) throw BadRequestException`. | **Khớp** |
| **Chuyển đổi trạng thái** | Chuyển sang `PENDING_APPROVAL` | `guaranteeRequest.setStatus(GuaranteeStatus.PENDING_APPROVAL)`. | **Khớp** |
| **Validate lại toàn bộ form (BR-12)** | Submit phải validate lại toàn bộ các trường nghiệp vụ | Hàm `validateBeforeSubmit()` kiểm tra: CIF, GuaranteeType, Amount > 0, EffectiveDate, ExpiryDate > EffectiveDate, TenderNumber khi BID_BOND, Purpose, BeneficiaryName, ContactEmail, Phone. | **Khớp về cơ bản** |
| **Điểm lệch trong validate submit** | - Phone: `^[0-9]{10}$` (Đề bài: `^[0-9]{9,15}$`).<br>- Amount: Chưa kiểm tra trần `<= 1,000,000,000,000` (BR-01) trong `validateBeforeSubmit`.<br>- Email: Mới kiểm tra `!isBlank()`, chưa check định dạng regex email. |  | Cần cập nhật hàm `validateBeforeSubmit` cho chuẩn sát theo BR-01 và regex phone của đề bài. |
| **Lịch sử xử lý** | Ghi nhận action `SUBMIT` | `createHistory(saved, Action.SUBMIT, currentUser, null)`. | **Khớp** |

---

### 6. `POST /api/v1/guarantees/{id}/approve` (Checker phê duyệt - F4 Checker Action)

| Hạng mục đối chứng | Yêu cầu trong File Assignment | Hiện trạng Backend (`eGuarantee`) | Đánh giá & Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **Endpoint & Method** | `POST /api/v1/guarantees/:id/approve` | `POST /api/v1/guarantees/{id}/approve` | **Khớp** |
| **Phân quyền** | Chỉ Checker được Approve (BR-07, BR-08) | Security: `hasRole("CHECKER")`, Service: `currentUser.getRole() != Role.CHECKER`. | **Khớp** |
| **Trạng thái hợp lệ (BR-07)** | Chỉ hồ sơ ở trạng thái `PENDING_APPROVAL` mới được Approve | `if (status != PENDING_APPROVAL) throw BadRequestException`. | **Khớp** |
| **Trạng thái sau khi Approve** | Chuyển sang `APPROVED` | `guaranteeRequest.setStatus(GuaranteeStatus.APPROVED)`. | **Khớp** |
| **Lịch sử xử lý** | Ghi nhận action `APPROVE` | `createHistory(saved, Action.APPROVE, currentUser, null)`. | **Khớp** |

---

### 7. `POST /api/v1/guarantees/{id}/reject` (Checker từ chối - F4 Checker Action)

| Hạng mục đối chứng | Yêu cầu trong File Assignment | Hiện trạng Backend (`eGuarantee`) | Đánh giá & Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **Endpoint & Method** | `POST /api/v1/guarantees/:id/reject` | `POST /api/v1/guarantees/{id}/reject` | **Khớp** |
| **Phân quyền** | Chỉ Checker được Reject (BR-07, BR-08) | Security: `hasRole("CHECKER")`, Service: `currentUser.getRole() != Role.CHECKER`. | **Khớp** |
| **Trạng thái hợp lệ (BR-07)** | Chỉ hồ sơ `PENDING_APPROVAL` mới được Reject | `if (status != PENDING_APPROVAL) throw BadRequestException`. | **Khớp** |
| **Validate Reject Reason (BR-10)** | Bắt buộc nhập, độ dài từ 10 đến 500 ký tự | `RejectGuaranteeRequest`: `@NotBlank`, `@Size(min = 10, max = 500)`. | **Khớp tuyệt đối** |
| **Payload** | `{ "reason": "..." }` | Body khớp chuẩn DTO `RejectGuaranteeRequest`. | **Khớp** |
| **Trạng thái sau Reject** | Chuyển sang `REJECTED` | `guaranteeRequest.setStatus(GuaranteeStatus.REJECTED)`. | **Khớp** |
| **Lịch sử xử lý** | Ghi nhận action `REJECT` kèm comment là lý do | `createHistory(saved, Action.REJECT, currentUser, request.getReason().trim());`. | **Khớp tuyệt đối** |

---

## III. TỔNG HỢP CÁC ĐIỂM LỆCH VÀ DANH SÁCH CẦN SỬA (ACTION ITEMS)

### 1. Thiếu hụt nghiêm trọng (Critical)
* **Thiếu API `DELETE /api/v1/guarantees/{id}`**:
  * Đề bài yêu cầu: Maker được quyền xóa hồ sơ ở trạng thái `DRAFT` tại Frame 03 (BR-05).
  * Backend hiện tại hoàn toàn chưa có endpoint và method xử lý này.
  * **Giải pháp**: Thêm `@DeleteMapping("/{id}")` vào `GuaranteeController`, viết hàm `deleteGuarantee(id)` trong `GuaranteeService` (kiểm tra quyền Maker, kiểm tra trạng thái `DRAFT`, xóa bản ghi và các history liên quan nếu cần), và cấu hình quyền trong `SecurityConfiguration`.

### 2. Lệch chuẩn nghiệp vụ / Validation (Major)
* **Tên Role: `MARKER` vs `MAKER`**:
  * Đề bài (Mục 3, Mục 12): Dùng chuẩn `MAKER` (không có chữ `R` ở giữa).
  * BE hiện tại: Đặt enum là `Role.MARKER`, authority là `ROLE_MARKER`.
  * **Hệ quả**: Nếu FE đăng nhập nhận role `MAKER` hoặc mock user gửi role `MAKER` thì BE sẽ báo 403 Forbidden. Cần thống nhất quy ước giữa FE và BE (khuyến nghị chuẩn hóa thành `MAKER`).
* **Độ dài số điện thoại (`phoneNumber`)**:
  * Đề bài (Mục 6.3): Không bắt buộc (`Optional`), độ dài từ 9 đến 15 số (`^[0-9]{9,15}$`). Trong Liquibase database constraint cũng đang cấu hình `^[0-9]{9,15}$`.
  * BE Request & Submit validation: Đang bắt buộc 10 số (`^[0-9]{10}$`). Riêng `GuaranteeCreationRequest` còn gắn thêm `@NotBlank`.
  * **Giải pháp**: Cập nhật regex `@Pattern(regexp = "^[0-9]{9,15}$")` và bỏ `@NotBlank` để đúng đặc tả.
* **Validate cận trên số tiền trong Submit (BR-01)**:
  * Trong `validateBeforeSubmit()` tại `GuaranteeService`, mới chỉ kiểm tra `guaranteeAmount.signum() <= 0`, chưa kiểm tra `<=` 1.000.000.000.000. Nên bổ sung để đảm bảo nguyên tắc BR-12 (re-validate khi submit).

### 3. Lưu ý tích hợp Frontend (Integration Notes)
* **Response format**:
  * Backend luôn bọc response trong object dạng:
    ```json
    {
      "success": true,
      "message": "...",
      "data": { ... },
      "timestamp": "..."
    }
    ```
  * Frontend khi dùng Axios / TanStack Query cần extract: `response.data.data`.
* **Phụ thuộc bảng Khách hàng (Customer Database)**:
  * Backend kiểm tra tồn tại của CIF trong database table `customers`. Khi test Frame 03 / Frame 04, cần dùng các mã CIF đã có sẵn trong bảng `customers` (hoặc tạo trước khách hàng qua API Customer).
