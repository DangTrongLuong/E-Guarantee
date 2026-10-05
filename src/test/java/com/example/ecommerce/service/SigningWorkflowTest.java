package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.FileUploadResponse;
import com.example.ecommerce.entity.*;
import com.example.ecommerce.enums.*;
import com.example.ecommerce.exception.*;
import com.example.ecommerce.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningWorkflowTest {
    GuaranteeRequestRepository guarantees = mock(GuaranteeRequestRepository.class);
    GuaranteeFileRepository files = mock(GuaranteeFileRepository.class);
    ProcessingHistoryRepository histories = mock(ProcessingHistoryRepository.class);
    UserRepository users = mock(UserRepository.class);
    FileUploadServiceImpl uploads = mock(FileUploadServiceImpl.class);
    DocumentDownloadService downloads = mock(DocumentDownloadService.class);
    OfficeConversionService converter = mock(OfficeConversionService.class);
    DocumentSigningService signer = mock(DocumentSigningService.class);
    DocumentValidator validator = new DocumentValidator();
    User checker = User.builder().username("checker").role(Role.CHECKER).build();
    Map<String, GuaranteeRequest> requests = new ConcurrentHashMap<>();
    Map<String, GuaranteeFile> documents = new ConcurrentHashMap<>();
    SigningWorkflowService workflow;
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch entered = new CountDownLatch(1);

    @BeforeEach void setup() throws Exception {
        when(guarantees.findById(anyString())).thenAnswer(i -> Optional.ofNullable(requests.get(i.getArgument(0))));
        when(guarantees.findForSigning(anyString())).thenAnswer(i -> Optional.ofNullable(requests.get(i.getArgument(0))));
        when(files.findByPublicId(anyString())).thenAnswer(i -> Optional.ofNullable(documents.get(i.getArgument(0))));
        when(files.findById(anyLong())).thenAnswer(i -> documents.values().stream().filter(f -> f.getId().equals(i.getArgument(0))).findFirst());
        when(files.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.findByUsername("checker")).thenReturn(Optional.of(checker));
        when(downloads.download("source-url")).thenReturn(DocumentSigningTest.pdf());
        when(downloads.download("prepared-url")).thenReturn(DocumentSigningTest.docx());
        when(signer.signAndVerify(any(), anyString())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timeout");
            return i.getArgument(0);
        });
        var manager = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
            @Override protected void doCommit(DefaultTransactionStatus status) {}
            @Override protected void doRollback(DefaultTransactionStatus status) {}
        };
        workflow = new SigningWorkflowService(guarantees, files, histories, users, uploads, validator, downloads, converter, signer, manager);
        add("G1", 1L);
    }
    @AfterEach void stop() { release.countDown(); workflow.shutdown(); }
    GuaranteeFile add(String id, Long fileId) {
        requests.put(id, GuaranteeRequest.builder().id(id).status(GuaranteeStatus.APPROVED).signatureStatus(SignatureStatus.UNSIGNED).build());
        var file = GuaranteeFile.builder().id(fileId).guaranteeId(id).publicId("source-" + id).fileName("file.pdf")
                .fileUrl("source-url").format("pdf").requiresSigning(true).build();
        documents.put(file.getPublicId(), file);
        return file;
    }
    GuaranteeFile prepared() throws Exception {
        documents.get("source-G1").setFileName("file.doc");
        byte[] bytes = DocumentSigningTest.docx();
        when(downloads.download("prepared-url")).thenReturn(bytes);
        var file = GuaranteeFile.builder().id(2L).guaranteeId("G1").publicId("prepared-G1").fileName("file.docx")
                .format("docx").fileUrl("prepared-url").sourceFileId(1L).artifactType(FileArtifactType.PREPARED)
                .preparedBy("checker").preparedAt(LocalDateTime.now()).sha256(DocumentValidator.sha256(bytes)).build();
        documents.put(file.getPublicId(), file);
        return file;
    }
    @Test void rejectsUnattachedFilesWrongRoleAndMissingConfirmation() throws Exception {
        var source = documents.get("source-G1");
        source.setGuaranteeId(null);
        assertThrows(BadRequestException.class, () -> workflow.requestSign("G1", source.getPublicId(), null, null, checker));
        source.setGuaranteeId("G1");
        assertThrows(BadRequestException.class, () -> workflow.requestSign("G1", source.getPublicId(), null, null,
                User.builder().username("maker").role(Role.MARKER).build()));
        prepared();
        assertThrows(BadRequestException.class, () -> workflow.requestSign("G1", source.getPublicId(), null, null, checker));
        assertEquals(SignatureStatus.UNSIGNED, requests.get("G1").getSignatureStatus());
        verifyNoInteractions(signer);
    }
    @Test void rejectsWrongHashWrongOwnerExpiryAndChangedBytes() throws Exception {
        var prepared = prepared();
        assertThrows(ConflictException.class, () -> workflow.requestSign("G1", "source-G1", prepared.getPublicId(), "wrong", checker));
        prepared.setPreparedBy("other");
        assertThrows(ConflictException.class, () -> workflow.requestSign("G1", "source-G1", prepared.getPublicId(), prepared.getSha256(), checker));
        prepared.setPreparedBy("checker"); prepared.setPreparedAt(LocalDateTime.now().minusHours(25));
        assertThrows(ConflictException.class, () -> workflow.requestSign("G1", "source-G1", prepared.getPublicId(), prepared.getSha256(), checker));
        prepared.setPreparedAt(LocalDateTime.now());
        when(downloads.download("prepared-url")).thenReturn("changed".getBytes());
        assertThrows(ConflictException.class, () -> workflow.requestSign("G1", "source-G1", prepared.getPublicId(), prepared.getSha256(), checker));
        assertEquals(SignatureStatus.UNSIGNED, requests.get("G1").getSignatureStatus());
    }
    @Test void signsConfirmedCopyAndKeepsOriginal() throws Exception {
        var prepared = prepared();
        var artifact = GuaranteeFile.builder().id(3L).publicId("signed-G1").fileName("signed_file.docx").format("docx").build();
        documents.put(artifact.getPublicId(), artifact);
        when(uploads.storeArtifact(any(), eq(true))).thenReturn(FileUploadResponse.builder().status("SUCCESS").publicId("signed-G1").build());
        workflow.requestSign("G1", "source-G1", prepared.getPublicId(), prepared.getSha256(), checker);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertEquals(SignatureStatus.PENDING_SIGN, requests.get("G1").getSignatureStatus());
        assertThrows(ConflictException.class, () -> workflow.requestSign("G1", "source-G1", prepared.getPublicId(), prepared.getSha256(), checker));
        release.countDown(); workflow.shutdown();
        assertEquals(SignatureStatus.SIGNED, requests.get("G1").getSignatureStatus());
        assertEquals(FileArtifactType.SIGNED, artifact.getArtifactType());
        assertEquals(prepared.getId(), artifact.getSourceFileId());
        assertEquals("source-url", documents.get("source-G1").getFileUrl());
        verify(signer).signAndVerify(any(), eq("docx"));
    }
    @Test void uploadFailureMarksFailedAndPreservesOriginal() throws Exception {
        when(uploads.storeArtifact(any(), eq(true))).thenReturn(FileUploadResponse.builder().status("FAILED").build());
        workflow.requestSign("G1", "source-G1", null, null, checker);
        assertTrue(entered.await(3, TimeUnit.SECONDS)); release.countDown(); workflow.shutdown();
        assertEquals(SignatureStatus.FAILED, requests.get("G1").getSignatureStatus());
        assertEquals("source-url", documents.get("source-G1").getFileUrl());
        verify(histories, atLeast(2)).save(any());
    }
    @Test void saturatedQueueReturns503BeforeChangingState() throws Exception {
        when(uploads.storeArtifact(any(), eq(true))).thenReturn(FileUploadResponse.builder().status("FAILED").build());
        workflow.requestSign("G1", "source-G1", null, null, checker);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        for (int i = 2; i <= 11; i++) {
            add("G"+i, (long)i); workflow.requestSign("G"+i, "source-G"+i, null, null, checker);
        }
        add("G12", 12L);
        assertThrows(SigningBusyException.class, () -> workflow.requestSign("G12", "source-G12", null, null, checker));
        assertEquals(SignatureStatus.UNSIGNED, requests.get("G12").getSignatureStatus());
    }
    @Test void restartRecoversPendingUsingRecordedActor() {
        var req = requests.get("G1"); req.setSignatureStatus(SignatureStatus.PENDING_SIGN); req.setUpdatedBy(checker);
        when(guarantees.findBySignatureStatus(SignatureStatus.PENDING_SIGN)).thenReturn(List.of(req));
        workflow.recoverInterrupted();
        assertEquals(SignatureStatus.FAILED, req.getSignatureStatus());
        verify(histories).save(argThat(history -> history.getPerformedBy() == checker));
    }
    @Test void conversionErrorDoesNotChangeSigningStatus() throws Exception {
        documents.get("source-G1").setFileName("file.xls");
        byte[] legacy;
        try (var book = new org.apache.poi.hssf.usermodel.HSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
            book.createSheet(); book.write(out); legacy = out.toByteArray();
        }
        when(downloads.download("source-url")).thenReturn(legacy);
        when(converter.convert(any(), eq("xls"))).thenThrow(new java.net.http.HttpTimeoutException("timeout"));
        assertThrows(BadRequestException.class, () -> workflow.prepare("G1", "source-G1", checker));
        assertEquals(SignatureStatus.UNSIGNED, requests.get("G1").getSignatureStatus());
        verifyNoInteractions(uploads);
    }
}
