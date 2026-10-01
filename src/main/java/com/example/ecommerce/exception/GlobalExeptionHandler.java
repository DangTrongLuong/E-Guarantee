package com.example.ecommerce.exception;

import com.example.ecommerce.dto.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExeptionHandler {
    @ExceptionHandler(SigningBusyException.class)
    public ResponseEntity<ApiResponse<Object>> handleSigningBusy(SigningBusyException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SIGNING_BUSY", ex.getMessage());
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Không tìm thấy tài nguyên: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Không tìm thấy các entity: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler({ BadRequestException.class, IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(RuntimeException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflict(ConflictException ex) {
        log.warn("Xung đột nghiệp vụ: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Tài nguyên bị trùng lặp: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String rootMsg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();

        log.error("Vi phạm tính toàn vẹn dữ liệu: {}", rootMsg, ex);

        if (rootMsg != null) {
            if (rootMsg.contains("fk_gr_customer_cif")) {
                return build(HttpStatus.CONFLICT, "CUSTOMER_HAS_GUARANTEES",
                        "Không thể xóa hoặc thay đổi thông tin khách hàng do đang có yêu cầu bảo lãnh liên kết trong hệ thống!");
            }
            if (rootMsg.contains("fk_ph_guarantee_id")) {
                return build(HttpStatus.CONFLICT, "GUARANTEE_HAS_HISTORIES",
                        "Không thể xóa yêu cầu bảo lãnh do đang có lịch sử xử lý liên kết trong hệ thống!");
            }
            if (rootMsg.contains("fk_ph_performed_by") || rootMsg.contains("fk_gr_created_by")
                    || rootMsg.contains("fk_gr_updated_by")) {
                return build(HttpStatus.CONFLICT, "USER_HAS_ASSOCIATED_DATA",
                        "Không thể xóa người dùng do đang có dữ liệu bảo lãnh hoặc lịch sử thao tác liên kết!");
            }
            if (rootMsg.contains("chk_gr_phone_number_format")) {
                return build(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER",
                        "Số điện thoại phải chứa từ 9 đến 15 chữ số");
            }
            if (rootMsg.contains("chk_gr_expiry_after_effective")) {
                return build(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY_DATE",
                        "Ngày hết hạn phải bằng hoặc sau ngày hiệu lực");
            }
            if (rootMsg.contains("chk_gr_tender_number_required")) {
                return build(HttpStatus.BAD_REQUEST, "MISSING_TENDER_NUMBER",
                        "Số hiệu gói thầu là bắt buộc đối với loại bảo lãnh dự thầu (BID_BOND)");
            }
            if (rootMsg.contains("chk_ph_reject_comment")) {
                return build(HttpStatus.BAD_REQUEST, "INVALID_REJECT_COMMENT",
                        "Lý do từ chối phải từ 10 đến 500 ký tự");
            }
        }

        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "Dữ liệu vi phạm ràng buộc trong hệ thống (trùng dữ liệu hoặc liên kết không hợp lệ)");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        log.warn("Xác thực thất bại: {}", details);
        ApiResponse<Object> body = ApiResponse.error("VALIDATION_FAILED",
                "Dữ liệu request không hợp lệ", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Dữ liệu JSON không đọc được hoặc sai kiểu dữ liệu: {}", ex.getMessage());

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife) {
            String fieldName = ife.getPath().stream()
                    .map(ref -> ref.getFieldName())
                    .filter(name -> name != null)
                    .reduce((first, second) -> second)
                    .orElse("dữ liệu");

            if ("guaranteeAmount".equals(fieldName)) {
                return build(HttpStatus.BAD_REQUEST, "INVALID_GUARANTEE_AMOUNT",
                        "Số tiền bảo lãnh không hợp lệ, phải là định dạng số (ví dụ: 5000000000)");
            }
            if ("effectiveDate".equals(fieldName) || "expiryDate".equals(fieldName)) {
                return build(HttpStatus.BAD_REQUEST, "INVALID_DATE_FORMAT",
                        "Định dạng ngày tháng không hợp lệ (định dạng chuẩn YYYY-MM-DD)");
            }

            return build(HttpStatus.BAD_REQUEST, "INVALID_FIELD_FORMAT",
                    "Trường '" + fieldName + "' chứa dữ liệu không đúng định dạng mong muốn");
        }

        return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "Dữ liệu gửi lên không đúng định dạng JSON hoặc kiểu dữ liệu không hợp lệ");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Kích thước file upload vượt quá giới hạn cho phép: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MAX_UPLOAD_SIZE_EXCEEDED",
                "Dung lượng file upload vượt quá giới hạn tối đa cho phép (Tối đa 100MB/file, tổng 200MB/lần request)");
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiResponse<Object>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
