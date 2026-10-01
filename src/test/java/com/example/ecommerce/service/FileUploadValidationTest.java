package com.example.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.ecommerce.entity.GuaranteeFile;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.repository.GuaranteeFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileUploadValidationTest {
    Cloudinary cloud = mock(Cloudinary.class);
    GuaranteeFileRepository files = mock(GuaranteeFileRepository.class);
    FileUploadServiceImpl uploads = new FileUploadServiceImpl(cloud, files, new DocumentValidator(), Runnable::run);

    @Test void batchUploadHashesBeforeMultipartFileIsMoved() throws Exception {
        byte[] content = DocumentSigningTest.pdf();
        var file = movingFile(content);
        var uploader = mock(Uploader.class);
        when(cloud.uploader()).thenReturn(uploader);
        var uploadedPath = new AtomicReference<Path>();
        when(uploader.upload(any(File.class), anyMap())).thenAnswer(invocation -> {
            File uploaded = invocation.getArgument(0);
            uploadedPath.set(uploaded.toPath());
            assertArrayEquals(content, Files.readAllBytes(uploaded.toPath()));
            return Map.of("public_id", "uploaded-pdf", "secure_url", "https://example.com/file.pdf");
        });
        when(files.save(any(GuaranteeFile.class))).thenAnswer(invocation -> {
            GuaranteeFile saved = invocation.getArgument(0);
            assertEquals(DocumentValidator.sha256(content), saved.getSha256());
            saved.setId(42L);
            return saved;
        });

        var response = uploads.uploadFiles(List.of(file), false);

        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertEquals("uploaded-pdf", response.getFiles().getFirst().getPublicId());
        assertEquals(DocumentValidator.sha256(content), response.getFiles().getFirst().getSha256());
        assertFalse(Files.exists(uploadedPath.get()));
        verify(uploader, never()).destroy(anyString(), anyMap());
    }

    @Test void databaseFailureRemovesCloudinaryUploadAndTemporaryFile() throws Exception {
        var uploader = mock(Uploader.class);
        when(cloud.uploader()).thenReturn(uploader);
        var uploadedPath = new AtomicReference<Path>();
        when(uploader.upload(any(File.class), anyMap())).thenAnswer(invocation -> {
            uploadedPath.set(((File) invocation.getArgument(0)).toPath());
            return Map.of("public_id", "orphan-pdf", "secure_url", "https://example.com/file.pdf");
        });
        when(files.save(any(GuaranteeFile.class))).thenThrow(new IllegalStateException("Database unavailable"));

        var response = uploads.uploadSingleFile(movingFile(DocumentSigningTest.pdf()), false);

        assertEquals("FAILED", response.getStatus());
        verify(uploader).destroy("orphan-pdf", Map.of("resource_type", "raw"));
        assertFalse(Files.exists(uploadedPath.get()));
    }

    private MockMultipartFile movingFile(byte[] content) {
        return new MockMultipartFile("files", "file.pdf", "application/pdf", content) {
            boolean moved;
            @Override public void transferTo(File destination) throws IOException {
                super.transferTo(destination);
                moved = true;
            }
            @Override public byte[] getBytes() throws IOException {
                if (moved) throw new IOException("Servlet temporary file was moved");
                return super.getBytes();
            }
        };
    }

    @Test void clientCannotClaimSignedStatus() throws Exception {
        var file = new MockMultipartFile("files", "file.pdf", "application/pdf", DocumentSigningTest.pdf());
        assertThrows(BadRequestException.class, () -> uploads.uploadSingleFile(file, true));
        assertThrows(BadRequestException.class, () -> uploads.uploadSingleFileAsync(file, true));
        assertThrows(BadRequestException.class, () -> uploads.uploadFiles(List.of(file), true));
        verifyNoInteractions(cloud, files);
    }
    @Test void validatesEntireBatchBeforeAnyUpload() throws Exception {
        var good = new MockMultipartFile("files", "file.PDF", "text/plain", DocumentSigningTest.pdf());
        var bad = new MockMultipartFile("files", "file.docx", "application/pdf", DocumentSigningTest.pdf());
        assertThrows(BadRequestException.class, () -> uploads.uploadFiles(List.of(good, bad), false));
        assertThrows(BadRequestException.class, () -> uploads.uploadFiles(java.util.Arrays.asList(good, null), false));
        assertThrows(BadRequestException.class, () -> uploads.uploadSingleFile(
                new MockMultipartFile("files", "file.exe", "application/pdf", DocumentSigningTest.pdf()), false));
        verifyNoInteractions(cloud, files);
    }
}
