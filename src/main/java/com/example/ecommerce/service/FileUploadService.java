package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.FileUploadBatchResponse;
import com.example.ecommerce.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FileUploadService {

    FileUploadBatchResponse uploadFiles(List<MultipartFile> files, Boolean isDigitallySigned);

    FileUploadResponse uploadSingleFile(MultipartFile file, Boolean isDigitallySigned);

    CompletableFuture<FileUploadResponse> uploadSingleFileAsync(MultipartFile file, Boolean isDigitallySigned);

    boolean deleteFile(String publicId);
}
