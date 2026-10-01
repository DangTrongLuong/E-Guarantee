package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.GuaranteeCreationRequest;
import com.example.ecommerce.dto.request.GuaranteeUpdateRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.GuaranteeResponse;
import com.example.ecommerce.dto.response.GuaranteeSummaryResponse;
import com.example.ecommerce.service.GuaranteeService;
import com.example.ecommerce.service.JasperReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ecommerce.dto.request.RejectGuaranteeRequest;
import com.example.ecommerce.dto.response.ProcessingHistoryResponse;

import java.util.List;

import com.example.ecommerce.dto.response.PageResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/guarantees")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
@Tag(name = "Guarantee Request", description = "Endpoints quản lý yêu cầu bảo lãnh (Create DRAFT & Update)")
public class GuaranteeController {

    GuaranteeService guaranteeService;
    JasperReportService jasperReportService;

    @PostMapping
    @Operation(
            summary = "Tạo mới yêu cầu bảo lãnh (Bản nháp - DRAFT)",
            description = "Tạo mới một hồ sơ yêu cầu bảo lãnh ở trạng thái DRAFT"
    )
    public ResponseEntity<ApiResponse<GuaranteeResponse>> createGuarantee(
            @Valid @RequestBody GuaranteeCreationRequest request
    ) {
        log.info("Nhận yêu cầu tạo mới hồ sơ bảo lãnh");
        GuaranteeResponse response = guaranteeService.createGuarantee(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo mới yêu cầu bảo lãnh ở trạng thái Bản nháp (DRAFT) thành công", response));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Cập nhật yêu cầu bảo lãnh",
            description = "Chỉnh sửa thông tin hồ sơ ở trạng thái DRAFT hoặc REJECTED. Hồ sơ REJECTED sau khi lưu sẽ chuyển về DRAFT"
    )
    public ResponseEntity<ApiResponse<GuaranteeResponse>> updateGuarantee(
            @PathVariable String id,
            @Valid @RequestBody GuaranteeUpdateRequest request
    ) {
        log.info("Nhận yêu cầu cập nhật hồ sơ bảo lãnh {}", id);
        GuaranteeResponse response = guaranteeService.updateGuarantee(id, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật yêu cầu bảo lãnh thành công", response));
    }

    @GetMapping
    @Operation(
            summary = "Lấy danh sách yêu cầu bảo lãnh",
            description = "Hỗ trợ lọc theo mã yêu cầu, tên khách hàng, CIF, mã số thuế, trạng thái, loại bảo lãnh, khoảng ngày tạo; hỗ trợ phân trang và sắp xếp nhiều trường (sortBy=field,direction lặp nhiều lần)"
    )
    public ResponseEntity<ApiResponse<PageResponse<GuaranteeSummaryResponse>>> getGuarantees(
            @RequestParam(required = false) Map<String, Object> params,
            @RequestParam(name = "sortBy", required = false) List<String> sortByParams
    ) {
        log.info("Nhận yêu cầu lấy danh sách yêu cầu bảo lãnh với params: {}, sortBy: {}", params, sortByParams);
        PageResponse<GuaranteeSummaryResponse> response = guaranteeService.getGuarantees(params, sortByParams);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy danh sách yêu cầu bảo lãnh thành công", response));
    }

    @GetMapping(value = {"/status-counts", "/status-count", "/counts"})
    @Operation(
            summary = "Lấy số lượng yêu cầu bảo lãnh theo từng trạng thái",
            description = "Trả về tổng số lượng bản ghi (ALL) và số lượng theo từng trạng thái (DRAFT, PENDING_APPROVAL, APPROVED, REJECTED). Hỗ trợ các tham số lọc tìm kiếm (từ khóa, mã khách hàng, ngày tạo, loại bảo lãnh, ...)"
    )
    public ResponseEntity<ApiResponse<Map<String, Long>>> getGuaranteeStatusCounts(
            @RequestParam(required = false) Map<String, Object> params
    ) {
        log.info("Nhận yêu cầu lấy số lượng yêu cầu bảo lãnh theo trạng thái với params: {}", params);
        Map<String, Long> response = guaranteeService.getGuaranteeStatusCounts(params);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy số lượng yêu cầu bảo lãnh theo trạng thái thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy chi tiết yêu cầu bảo lãnh",
            description = "Lấy thông tin chi tiết của một yêu cầu bảo lãnh theo mã"
    )
    public ResponseEntity<ApiResponse<GuaranteeResponse>> getDetail(
            @PathVariable String id
    ) {
        GuaranteeResponse response = guaranteeService.getDetail(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy chi tiết yêu cầu bảo lãnh thành công", response));
    }

    @GetMapping("/{id}/histories")
    @Operation(
            summary = "Lấy lịch sử xử lý yêu cầu bảo lãnh",
            description = "Lấy danh sách lịch sử xử lý theo thứ tự thời gian"
    )
    public ResponseEntity<ApiResponse<List<ProcessingHistoryResponse>>> getHistories(
            @PathVariable String id
    ) {
        List<ProcessingHistoryResponse> response =
                guaranteeService.getHistories(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy lịch sử xử lý thành công", response));
    }

    @PostMapping("/{id}/submit")
    @Operation(
            summary = "Gửi yêu cầu bảo lãnh phê duyệt",
            description = "MARKER gửi hồ sơ ở trạng thái DRAFT sang PENDING_APPROVAL"
    )
    public ResponseEntity<ApiResponse<GuaranteeResponse>> submit(
            @PathVariable String id
    ) {
        GuaranteeResponse response = guaranteeService.submit(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Gửi yêu cầu bảo lãnh phê duyệt thành công", response));
    }

    @PostMapping("/{id}/approve")
    @Operation(
            summary = "Phê duyệt yêu cầu bảo lãnh",
            description = "CHECKER phê duyệt hồ sơ ở trạng thái PENDING_APPROVAL"
    )
    public ResponseEntity<ApiResponse<GuaranteeResponse>> approve(
            @PathVariable String id
    ) {
        GuaranteeResponse response = guaranteeService.approve(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Phê duyệt yêu cầu bảo lãnh thành công", response));
    }

    @PostMapping("/{id}/reject")
    @Operation(
            summary = "Từ chối yêu cầu bảo lãnh",
            description = "CHECKER từ chối hồ sơ ở trạng thái PENDING_APPROVAL, lý do từ 10 đến 500 ký tự"
    )
    public ResponseEntity<ApiResponse<GuaranteeResponse>> reject(
            @PathVariable String id,
            @Valid @RequestBody RejectGuaranteeRequest request
    ) {
        GuaranteeResponse response = guaranteeService.reject(id, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Từ chối yêu cầu bảo lãnh thành công", response));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Xóa yêu cầu bảo lãnh (Bản nháp - DRAFT)",
            description = "Chỉ cho phép xóa bản ghi ở trạng thái DRAFT"
    )
    public ResponseEntity<ApiResponse<Void>> deleteGuarantee(
            @PathVariable String id
    ) {
        log.info("Nhận yêu cầu xóa hồ sơ bảo lãnh {}", id);
        guaranteeService.deleteGuarantee(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Xóa yêu cầu bảo lãnh thành công", null));
    }

    @GetMapping("/{id}/export-pdf")
    @Operation(
            summary = "Xuất file PDF báo cáo bảo lãnh từ mẫu Jasper",
            description = "Tự động lấy thông tin hồ sơ bảo lãnh theo ID và sinh file PDF từ mẫu DetalGuarantee.jrxml"
    )
    public ResponseEntity<byte[]> exportGuaranteePdf(@PathVariable String id) {
        log.info("Nhận yêu cầu xuất PDF báo cáo bảo lãnh cho mã {}", id);
        GuaranteeResponse detail = guaranteeService.getDetail(id);
        byte[] pdfBytes = jasperReportService.generateGuaranteePdf(detail);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("Guarantee_Detail_" + id + ".pdf")
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @PostMapping("/export-pdf")
    @Operation(
            summary = "Xuất file PDF báo cáo bảo lãnh với tham số tùy chỉnh",
            description = "Nhận các tham số tùy chỉnh trong Body (JSON) và sinh file PDF từ mẫu DetalGuarantee.jrxml"
    )
    public ResponseEntity<byte[]> exportGuaranteePdfFromCustomParams(@RequestBody Map<String, Object> params) {
        log.info("Nhận yêu cầu xuất PDF báo cáo bảo lãnh với tham số tùy chỉnh: {}", params);
        byte[] pdfBytes = jasperReportService.generatePdfFromCustomParams(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("Guarantee_Detail_Report.pdf")
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}

