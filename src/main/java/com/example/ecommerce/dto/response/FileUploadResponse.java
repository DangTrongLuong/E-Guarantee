package com.example.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private Long id;
    private String fileName;
    private String fileUrl;
    private String publicId;
    private String format;
    private Long fileSize;
    private String resourceType;
    private Boolean isDigitallySigned;
    private LocalDateTime uploadedAt;
    private String status;
    private String errorMessage;
}
