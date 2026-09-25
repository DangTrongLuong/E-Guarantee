package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.FileUploadBatchResponse;
import com.example.ecommerce.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FileUploadService {

    FileUploadBatchResponse uploadFiles(List<MultipartFile> files);

    FileUploadResponse uploadSingleFile(MultipartFile file);

    CompletableFuture<FileUploadResponse> uploadSingleFileAsync(MultipartFile file);

    boolean deleteFile(String publicId);
}
