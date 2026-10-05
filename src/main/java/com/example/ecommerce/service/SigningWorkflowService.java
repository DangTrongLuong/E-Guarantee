package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.GuaranteeFileResponse;
import com.example.ecommerce.entity.*;
import com.example.ecommerce.enums.*;
import com.example.ecommerce.exception.*;
import com.example.ecommerce.repository.*;
import com.example.ecommerce.utils.ByteArrayMultipartFile;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.*;

@Service
@Slf4j
public class SigningWorkflowService {
    private final GuaranteeRequestRepository guarantees;
    private final GuaranteeFileRepository files;
    private final ProcessingHistoryRepository histories;
    private final UserRepository users;
    private final FileUploadServiceImpl uploads;
    private final DocumentValidator validator;
    private final DocumentDownloadService downloads;
    private final OfficeConversionService converter;
    private final DocumentSigningService signer;
    private final TransactionTemplate tx;
    private final Semaphore slots = new Semaphore(11);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10), runnable -> new Thread(runnable, "document-signing"), new ThreadPoolExecutor.AbortPolicy());

    public SigningWorkflowService(GuaranteeRequestRepository guarantees, GuaranteeFileRepository files,
            ProcessingHistoryRepository histories, UserRepository users, FileUploadServiceImpl uploads,
            DocumentValidator validator, DocumentDownloadService downloads, OfficeConversionService converter,
            DocumentSigningService signer, PlatformTransactionManager manager) {
        this.guarantees = guarantees; this.files = files; this.histories = histories; this.users = users;
        this.uploads = uploads; this.validator = validator; this.downloads = downloads;
        this.converter = converter; this.signer = signer;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public GuaranteeFileResponse prepare(String id, String publicId, User user) {
        requireChecker(user);
        checkEligible(requireGuarantee(id));
        GuaranteeFile source = original(id, publicId);
        String inputFormat = format(source);
        if (!inputFormat.equals("doc") && !inputFormat.equals("xls"))
            throw new BadRequestException("Chỉ DOC/XLS cần bước chuẩn bị chuyển đổi");
        String target = inputFormat.equals("doc") ? "docx" : "xlsx";
        String uploadedId = null;
        try {
            byte[] input = downloadValidated(source, false);
            byte[] converted = converter.convert(input, inputFormat);
            validator.validate(converted, target, false);
            var upload = uploads.storeArtifact(new ByteArrayMultipartFile(converted, "file",
                    renamed(source.getFileName(), "prepared_", target), mime(target)), false);
            if (!"SUCCESS".equals(upload.getStatus())) throw new IllegalStateException("Upload bản chuẩn bị thất bại");
            uploadedId = upload.getPublicId();
            String artifactId = uploadedId;
            return tx.execute(status -> {
                checkEligible(guarantees.findForSigning(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ")));
                GuaranteeFile currentSource = original(id, publicId);
                String sourceHash = DocumentValidator.sha256(input);
                if (currentSource.getSha256() != null && !sourceHash.equals(currentSource.getSha256()))
                    throw new ConflictException("Bản gốc đã thay đổi trong khi chuyển đổi");
                currentSource.setSha256(sourceHash);
                files.save(currentSource);
                GuaranteeFile artifact = files.findByPublicId(artifactId).orElseThrow();
                artifact.setGuaranteeId(id); artifact.setSourceFileId(currentSource.getId());
                artifact.setArtifactType(FileArtifactType.PREPARED);
                artifact.setPreparedBy(user.getUsername()); artifact.setPreparedAt(LocalDateTime.now());
                artifact.setFormat(target); artifact.setSha256(DocumentValidator.sha256(converted));
                files.save(artifact);
                return response(artifact);
            });
        } catch (RuntimeException e) {
            discard(uploadedId);
            throw e;
        } catch (Exception e) {
            discard(uploadedId);
            log.warn("Conversion failed for guarantee {}", id, e);
            throw new BadRequestException("Không chuyển đổi được file; hãy kiểm tra file hoặc thử lại");
        }
    }

    public void requestSign(String id, String publicId, String preparedPublicId, String preparedSha256, User user) {
        requireChecker(user);
        checkEligible(requireGuarantee(id));
        GuaranteeFile source = original(id, publicId);
        GuaranteeFile inputFile = source;
        String sourceFormat = format(source);
        boolean legacy = sourceFormat.equals("doc") || sourceFormat.equals("xls");
        if (legacy) {
            if (preparedPublicId == null || preparedPublicId.isBlank() || preparedSha256 == null || preparedSha256.isBlank())
                throw new BadRequestException("DOC/XLS cần xem bản chuẩn bị và gửi preparedPublicId, preparedSha256 để xác nhận");
            inputFile = prepared(id, source, preparedPublicId, preparedSha256, user);
        } else if (preparedPublicId != null || preparedSha256 != null) {
            throw new BadRequestException("Chỉ DOC/XLS sử dụng bản chuẩn bị");
        }
        if (executor.isShutdown() || !slots.tryAcquire()) throw new SigningBusyException("Hàng đợi ký đã đầy; thử lại sau");
        Path spool = null;
        try {
            byte[] bytes = downloadValidated(inputFile, false);
            if (legacy && !DocumentValidator.sha256(bytes).equals(preparedSha256))
                throw new ConflictException("Nội dung bản chuẩn bị đã thay đổi");
            spool = Files.createTempFile("eguarantee-sign-", ".input");
            Files.write(spool, bytes);
            Path jobInput = spool;
            Long inputId = inputFile.getId();
            String inputFormat = format(inputFile);
            String inputName = inputFile.getFileName();
            tx.executeWithoutResult(status -> {
                GuaranteeRequest req = guarantees.findForSigning(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ"));
                checkEligible(req);
                GuaranteeFile currentSource = original(id, publicId);
                if (legacy) prepared(id, currentSource, preparedPublicId, preparedSha256, user);
                req.setSignatureStatus(SignatureStatus.PENDING_SIGN); req.setUpdatedBy(user);
                req.setUpdatedDate(LocalDateTime.now()); guarantees.save(req);
                history(req, user, "Chờ ký số " + inputFormat);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { executor.execute(() -> execute(id, inputId, inputName, inputFormat, user.getUsername(), jobInput)); }
                        catch (RejectedExecutionException e) {
                            fail(id, user.getUsername(), "Hàng đợi ký không khả dụng");
                            cleanup(jobInput); slots.release();
                        }
                    }
                });
            });
        } catch (RuntimeException e) {
            cleanup(spool); slots.release(); throw e;
        } catch (Exception e) {
            cleanup(spool); slots.release();
            throw new BadRequestException("Không đọc hoặc chuẩn bị được tài liệu ký");
        }
    }

    public void cancelSign(String id, User user) {
        requireChecker(user);
        tx.executeWithoutResult(status -> {
            GuaranteeRequest req = guarantees.findForSigning(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ"));
            if (req.getSignatureStatus() != SignatureStatus.PENDING_SIGN)
                throw new BadRequestException("Chỉ có thể hủy khi hồ sơ đang chờ ký");
            req.setSignatureStatus(SignatureStatus.UNSIGNED);
            req.setUpdatedBy(user);
            req.setUpdatedDate(LocalDateTime.now());
            guarantees.save(req);
            history(req, user, "Người dùng hủy yêu cầu ký số");
        });
    }

    private void execute(String id, Long inputId, String name, String format, String username, Path input) {
        String uploadedId = null;
        try {
            byte[] signed = signer.signAndVerify(Files.readAllBytes(input), format);
            var result = uploads.storeArtifact(new ByteArrayMultipartFile(signed, "file",
                    renamed(name, "signed_", format), mime(format)), true);
            if (!"SUCCESS".equals(result.getStatus())) throw new IllegalStateException("Upload file ký thất bại");
            uploadedId = result.getPublicId();
            String artifactId = uploadedId;
            tx.executeWithoutResult(status -> {
                GuaranteeRequest req = guarantees.findForSigning(id).orElseThrow();
                if (req.getStatus() != GuaranteeStatus.APPROVED || req.getSignatureStatus() != SignatureStatus.PENDING_SIGN)
                    throw new ConflictException("Hồ sơ không còn chờ ký");
                GuaranteeFile source = files.findById(inputId).orElseThrow();
                if (!id.equals(source.getGuaranteeId())) throw new ConflictException("File đã đổi hồ sơ");
                GuaranteeFile artifact = files.findByPublicId(artifactId).orElseThrow();
                artifact.setGuaranteeId(id); artifact.setSourceFileId(inputId);
                artifact.setArtifactType(FileArtifactType.SIGNED); artifact.setFormat(format);
                files.save(artifact);
                User user = users.findByUsername(username).orElseThrow();
                req.setSignatureStatus(SignatureStatus.SIGNED); req.setUpdatedBy(user);
                req.setUpdatedDate(LocalDateTime.now()); guarantees.save(req);
                history(req, user, "Ký và xác thực " + format + " thành công; artifact=" + artifactId);
            });
        } catch (Exception e) {
            discard(uploadedId);
            log.error("Signing failed for guarantee {}", id, e);
            fail(id, username, e.getMessage());
        } finally { cleanup(input); slots.release(); }
    }

    private byte[] downloadValidated(GuaranteeFile file, boolean signed) throws Exception {
        byte[] bytes = downloads.download(file.getFileUrl());
        if (file.getSha256() != null && !file.getSha256().equals(DocumentValidator.sha256(bytes)))
            throw new ConflictException("File lưu trữ đã thay đổi so với bản ghi");
        validator.validate(bytes, format(file), signed);
        return bytes;
    }
    private GuaranteeRequest requireGuarantee(String id) {
        return guarantees.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ"));
    }
    private GuaranteeFile original(String id, String publicId) {
        GuaranteeFile file = files.findByPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy file"));
        if (!id.equals(file.getGuaranteeId())) throw new BadRequestException("File phải thuộc đúng hồ sơ");
        if (file.getArtifactType() != FileArtifactType.ORIGINAL || Boolean.TRUE.equals(file.getIsDigitallySigned()))
            throw new BadRequestException("Cần bản gốc chưa ký");
        if (!Boolean.TRUE.equals(file.getRequiresSigning()))
            throw new BadRequestException("File này là tài liệu đính kèm, không được phép ký số");
        return file;
    }
    private GuaranteeFile prepared(String id, GuaranteeFile source, String publicId, String hash, User user) {
        GuaranteeFile file = files.findByPublicId(publicId).orElseThrow(() -> new ConflictException("Không tìm thấy bản chuẩn bị"));
        String expectedFormat = format(source).equals("doc") ? "docx" : "xlsx";
        if (file.getArtifactType() != FileArtifactType.PREPARED || !Objects.equals(file.getSourceFileId(), source.getId())
                || !id.equals(file.getGuaranteeId()) || !user.getUsername().equals(file.getPreparedBy())
                || file.getPreparedAt() == null || file.getPreparedAt().isBefore(LocalDateTime.now().minusHours(24))
                || file.getPreparedAt().isAfter(LocalDateTime.now()) || !hash.equals(file.getSha256())
                || !expectedFormat.equals(format(file))) throw new ConflictException("Bản chuẩn bị không khớp, khác người tạo hoặc hết hạn");
        return file;
    }
    private void requireChecker(User user) {
        if (user == null || user.getRole() != Role.CHECKER) throw new BadRequestException("Chỉ CHECKER được ký số");
    }
    private void checkEligible(GuaranteeRequest req) {
        if (req.getStatus() != GuaranteeStatus.APPROVED) throw new BadRequestException("Chỉ hồ sơ APPROVED được ký");
        if (req.getSignatureStatus() == SignatureStatus.PENDING_SIGN)
            throw new ConflictException("Hồ sơ đang chờ ký file khác, vui lòng đợi");
    }
    private void history(GuaranteeRequest req, User user, String comment) {
        histories.save(ProcessingHistory.builder().guaranteeRequest(req).action(Action.SIGN).performedBy(user)
                .role(user == null ? Role.CHECKER : user.getRole()).timestamp(LocalDateTime.now()).comment(comment).build());
    }
    private void fail(String id, String username, String reason) {
        try {
            tx.executeWithoutResult(status -> guarantees.findForSigning(id).ifPresent(req -> {
                if (req.getSignatureStatus() != SignatureStatus.PENDING_SIGN) return;
                req.setSignatureStatus(SignatureStatus.FAILED); req.setUpdatedDate(LocalDateTime.now());
                guarantees.save(req);
                String message = "Ký thất bại: " + (reason == null ? "Lỗi xử lý" : reason);
                User actor = users.findByUsername(username).orElse(req.getUpdatedBy());
                if (actor != null) history(req, actor, message.substring(0, Math.min(message.length(), 480)));
            }));
        } catch (Exception e) { log.error("Cannot persist signing failure for {}", id, e); }
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (var req : guarantees.findBySignatureStatus(SignatureStatus.PENDING_SIGN))
            fail(req.getId(), "", "Tác vụ bị gián đoạn do ứng dụng khởi động lại; vui lòng thử lại");
    }
    @PreDestroy public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(180, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
    private void cleanup(Path path) {
        if (path != null) try { Files.deleteIfExists(path); } catch (Exception e) { log.warn("Cannot clean signing spool {}", path); }
    }
    private void discard(String publicId) {
        if (publicId == null) return;
        try { uploads.discardArtifact(publicId); }
        catch (Exception e) { log.error("Cannot clean orphan artifact {}", publicId, e); }
    }
    private String format(GuaranteeFile file) { return DocumentValidator.extension(file.getFileName()); }
    private String renamed(String name, String prefix, String format) {
        String base = name.substring(0, name.lastIndexOf('.'));
        return prefix + base.substring(0, Math.min(base.length(), 220)) + "." + format;
    }
    public static String mime(String format) {
        return switch (format) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xml" -> "application/xml";
            default -> "application/octet-stream";
        };
    }
    public static GuaranteeFileResponse response(GuaranteeFile file) {
        return GuaranteeFileResponse.builder().id(file.getId()).guaranteeId(file.getGuaranteeId()).fileName(file.getFileName())
                .fileUrl(file.getFileUrl()).publicId(file.getPublicId()).fileSize(file.getFileSize()).format(file.getFormat())
                .resourceType(file.getResourceType()).requiresSigning(file.getRequiresSigning()).isDigitallySigned(file.getIsDigitallySigned()).createdAt(file.getCreatedAt())
                .sourceFileId(file.getSourceFileId()).artifactType(file.getArtifactType()).sha256(file.getSha256())
                .preparedBy(file.getPreparedBy()).preparedAt(file.getPreparedAt()).build();
    }
}
