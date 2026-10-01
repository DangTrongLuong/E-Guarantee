
package com.example.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.ecommerce.dto.response.FileUploadBatchResponse;
import com.example.ecommerce.dto.response.FileUploadResponse;
import com.example.ecommerce.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.example.ecommerce.entity.GuaranteeFile;
import com.example.ecommerce.repository.GuaranteeFileRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final Cloudinary cloudinary;
    private final GuaranteeFileRepository guaranteeFileRepository;
    private final DocumentValidator documentValidator;

    @Qualifier("fileUploadExecutor")
    private final Executor fileUploadExecutor;

    private static final long MAX_SINGLE_FILE_SIZE = 100 * 1024 * 1024L;
    private static final long MAX_TOTAL_FILE_SIZE = 200 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "xml");

    @Override
    public FileUploadBatchResponse uploadFiles(List<MultipartFile> files, Boolean isDigitallySigned) {
        rejectSignedFlag(isDigitallySigned);
        validateBatchFiles(files);
        boolean signed = Boolean.TRUE.equals(isDigitallySigned);

        long totalSizeBytes = files.stream().mapToLong(MultipartFile::getSize).sum();

        List<CompletableFuture<FileUploadResponse>> futures = files.stream()
                .map(file -> uploadSingleFileAsync(file, signed))
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        allFutures.join();

        List<FileUploadResponse> responseList = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        int successCount = (int) responseList.stream()
                .filter(res -> "SUCCESS".equals(res.getStatus()))
                .count();

        int failedCount = responseList.size() - successCount;

        return FileUploadBatchResponse.builder()
                .totalFiles(files.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .totalSizeBytes(totalSizeBytes)
                .files(responseList)
                .build();
    }

    @Override
    public FileUploadResponse uploadSingleFile(MultipartFile file, Boolean isDigitallySigned) {
        rejectSignedFlag(isDigitallySigned);
        validateSingleFile(file);
        return uploadToCloudinary(file, Boolean.TRUE.equals(isDigitallySigned));
    }

    @Override
    public CompletableFuture<FileUploadResponse> uploadSingleFileAsync(MultipartFile file, Boolean isDigitallySigned) {
        rejectSignedFlag(isDigitallySigned);
        validateSingleFile(file);
        return CompletableFuture.supplyAsync(() -> uploadToCloudinary(file, Boolean.TRUE.equals(isDigitallySigned)),
                fileUploadExecutor);
    }

    @Override
    @Transactional
    public boolean deleteFile(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BadRequestException("publicId của file không được để trống!");
        }
        guaranteeFileRepository.findByPublicId(publicId).ifPresent(file -> {
            if (guaranteeFileRepository.existsBySourceFileId(file.getId()))
                throw new com.example.ecommerce.exception.ConflictException("Không xóa bản nguồn đang có bản chuyển đổi hoặc bản ký");
        });

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
            String resStatus = (String) result.get("result");

            if ("ok".equals(resStatus)) {
                guaranteeFileRepository.deleteByPublicId(publicId);
                log.info("Xóa file thành công trên Cloudinary & DB (publicId: {})", publicId);
                return true;
            }

            result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            boolean isOk = "ok".equals(result.get("result"));
            if (isOk) {
                guaranteeFileRepository.deleteByPublicId(publicId);
                log.info("Xóa file thành công trên Cloudinary & DB (publicId: {})", publicId);
                return true;
            } else {
                guaranteeFileRepository.findByPublicId(publicId).ifPresent(guaranteeFileRepository::delete);
                log.warn("Không tìm thấy file trên Cloudinary (publicId: {}), đã dọn dẹp bản ghi DB.", publicId);
                return true;
            }

        } catch (IOException e) {
            log.error("Lỗi khi xóa file trên Cloudinary (publicId: {}): {}", publicId, e.getMessage(), e);
            throw new BadRequestException("Lỗi khi xóa file trên Cloudinary: " + e.getMessage());
        }
    }

    private void validateSingleFile(MultipartFile file) {
        validateSingleFile(file, false);
    }

    private void validateSingleFile(MultipartFile file, boolean allowSignature) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File upload không được để trống!");
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new BadRequestException("Tên file upload không hợp lệ!");
        }

        if (file.getSize() > MAX_SINGLE_FILE_SIZE) {
            throw new BadRequestException(String.format(
                    "File '%s' vượt quá kích thước cho phép (%d MB / Tối đa 100MB)",
                    originalFilename, file.getSize() / (1024 * 1024)));
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BadRequestException(String.format(
                    "Định dạng file '.%s' của file '%s' không được hỗ trợ. Chỉ chấp nhận các định dạng: %s",
                    extension, originalFilename, String.join(", ", ALLOWED_EXTENSIONS)));
        }
        try {
            documentValidator.validate(file.getBytes(), extension.toLowerCase(java.util.Locale.ROOT), allowSignature);
        } catch (IOException e) {
            throw new BadRequestException("Không đọc được file upload");
        }
    }

    private void rejectSignedFlag(Boolean signed) {
        if (Boolean.TRUE.equals(signed))
            throw new BadRequestException("isDigitallySigned chỉ được đặt bởi luồng ký và xác thực phía server");
    }

    // Package-private: controllers cannot mark uploaded files as signed.
    FileUploadResponse storeArtifact(MultipartFile file, boolean signed) {
        validateSingleFile(file, signed);
        return uploadToCloudinary(file, signed);
    }

    void discardArtifact(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
        } catch (IOException e) { log.error("Cannot clean orphan artifact {}", publicId, e); }
        guaranteeFileRepository.findByPublicId(publicId).ifPresent(guaranteeFileRepository::delete);
    }

    private void validateBatchFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Danh sách file upload không được để trống!");
        }
        for (MultipartFile file : files) {
            if (file == null) throw new BadRequestException("Danh sách chứa file không hợp lệ");
        }

        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > MAX_TOTAL_FILE_SIZE) {
            throw new BadRequestException(String.format(
                    "Tổng dung lượng các file upload (%.2f MB) vượt quá giới hạn 200MB/lần",
                    (double) totalSize / (1024 * 1024)));
        }

        for (MultipartFile file : files) {
            validateSingleFile(file);
        }
    }

    @SuppressWarnings("unchecked")
    private FileUploadResponse uploadToCloudinary(MultipartFile file, boolean isDigitallySigned) {
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase();

        String baseName = originalFilename.contains(".")
                ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
                : originalFilename;

        String cleanBaseName = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String uniquePublicId = "eguarantee/documents/" + cleanBaseName + "_"
                + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

        Path tempFilePath = null;
        String uploadedPublicId = null;
        try {
            // transferTo may move the servlet's temporary file, making it unreadable afterward.
            String sha256 = DocumentValidator.sha256(file.getBytes());
            tempFilePath = Files.createTempFile("upload_", "_" + cleanBaseName + "." + extension);
            File tempFile = tempFilePath.toFile();
            file.transferTo(tempFile);

            Map<String, Object> options = ObjectUtils.asMap(
                    "public_id", uniquePublicId,
                    "resource_type", "raw",
                    "access_mode", "public",
                    "overwrite", false,
                    "chunk_size", 20 * 1024 * 1024);

            Map<?, ?> result = file.getSize() > 20 * 1024 * 1024
                    ? cloudinary.uploader().uploadLarge(tempFile, options)
                    : cloudinary.uploader().upload(tempFile, options);

            String fileUrl = (String) result.get("secure_url");
            if (fileUrl == null) {
                fileUrl = (String) result.get("url");
            }

            String publicId = (String) result.get("public_id");
            uploadedPublicId = publicId;
            if (publicId == null || fileUrl == null) throw new IOException("Cloudinary returned no file identifier or URL");
            String format = result.get("format") != null ? result.get("format").toString() : extension;
            Long fileSize = result.get("bytes") != null ? Long.parseLong(result.get("bytes").toString())
                    : file.getSize();
            String resourceType = result.get("resource_type") != null ? result.get("resource_type").toString() : "raw";

            GuaranteeFile guaranteeFile = GuaranteeFile.builder()
                    .fileName(originalFilename)
                    .fileUrl(fileUrl)
                    .publicId(publicId)
                    .fileSize(fileSize)
                    .format(format)
                    .resourceType(resourceType)
                    .isDigitallySigned(isDigitallySigned)
                    .sha256(sha256)
                    .build();

            GuaranteeFile savedFile = guaranteeFileRepository.save(guaranteeFile);

            return FileUploadResponse.builder()
                    .id(savedFile.getId())
                    .fileName(originalFilename)
                    .fileUrl(fileUrl)
                    .publicId(publicId)
                    .format(format)
                    .fileSize(fileSize)
                    .resourceType(resourceType)
                    .isDigitallySigned(savedFile.getIsDigitallySigned())
                    .artifactType(savedFile.getArtifactType())
                    .sourceFileId(savedFile.getSourceFileId())
                    .sha256(savedFile.getSha256())
                    .uploadedAt(savedFile.getCreatedAt())
                    .status("SUCCESS")
                    .build();

        } catch (IOException | RuntimeException e) {
            if (uploadedPublicId != null) {
                try { cloudinary.uploader().destroy(uploadedPublicId, ObjectUtils.asMap("resource_type", "raw")); }
                catch (Exception cleanupError) { log.error("Cannot remove orphan upload {}", uploadedPublicId, cleanupError); }
            }
            log.error("Lỗi upload file '{}' lên Cloudinary: {}", originalFilename, e.getMessage(), e);
            return FileUploadResponse.builder()
                    .fileName(originalFilename)
                    .uploadedAt(LocalDateTime.now())
                    .status("FAILED")
                    .errorMessage("Upload thất bại: " + e.getMessage())
                    .build();
        } finally {
            if (tempFilePath != null) {
                try {
                    Files.deleteIfExists(tempFilePath);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
