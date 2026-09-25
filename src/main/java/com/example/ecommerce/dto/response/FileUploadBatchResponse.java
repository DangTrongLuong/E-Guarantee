package com.example.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadBatchResponse {
    private int totalFiles;
    private int successCount;
    private int failedCount;
    private long totalSizeBytes;
    private List<FileUploadResponse> files;
}
