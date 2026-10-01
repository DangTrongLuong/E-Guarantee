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
public class GuaranteeFileResponse {
    private Long id;
    private String guaranteeId;
    private String fileName;
    private String fileUrl;
    private String publicId;
    private Long fileSize;
    private String format;
    private String resourceType;
    private Long sourceFileId;
    private com.example.ecommerce.enums.FileArtifactType artifactType;
    private String sha256;
    private String preparedBy;
    private LocalDateTime preparedAt;
    private Boolean isDigitallySigned;
    private LocalDateTime createdAt;
}
