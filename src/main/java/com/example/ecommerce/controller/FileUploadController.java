package com.example.ecommerce.controller;

import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.FileUploadBatchResponse;
import com.example.ecommerce.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Upload API", description = "API quản lý tải lên & xóa file bất đồng bộ đa luồng trên Cloudinary")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @Operation(summary = "Upload 1 hoặc nhiều file bất đồng bộ đa luồng qua Server (Tối đa 100MB/file, tổng 200MB)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadBatchResponse>> uploadFiles(
            @RequestParam("files") List<MultipartFile> files) {

        FileUploadBatchResponse response = fileUploadService.uploadFiles(files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Upload danh sách file thành công", response));
    }

    @Operation(summary = "Xóa file trên Cloudinary bằng publicId")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Boolean>> deleteFile(
            @RequestParam("publicId") String publicId) {

        boolean isDeleted = fileUploadService.deleteFile(publicId);
        if (isDeleted) {
            return ResponseEntity.ok(ApiResponse.success("Xóa file thành công", true));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("DELETE_FAILED", "Xóa file thất bại hoặc không tìm thấy file"));
        }
    }
}
