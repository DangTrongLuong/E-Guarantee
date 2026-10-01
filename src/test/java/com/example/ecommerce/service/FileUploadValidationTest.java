package com.example.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.repository.GuaranteeFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileUploadValidationTest {
    Cloudinary cloud = mock(Cloudinary.class);
    GuaranteeFileRepository files = mock(GuaranteeFileRepository.class);
    FileUploadServiceImpl uploads = new FileUploadServiceImpl(cloud, files, new DocumentValidator(), Runnable::run);

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
