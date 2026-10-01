package com.example.ecommerce.service;

import com.example.ecommerce.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.openxml4j.opc.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentSigningTest {
    @TempDir Path temp;
    DocumentValidator validator = new DocumentValidator();
    DocumentSigningService signing;

    @BeforeEach void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var dn = new X500Name("CN=eGuarantee Test");
        var builder = new JcaX509v3CertificateBuilder(dn, BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(60)), Date.from(Instant.now().plusSeconds(3600)), dn, pair.getPublic());
        var certificate = new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        KeyStore store = KeyStore.getInstance("PKCS12"); store.load(null, null);
        store.setKeyEntry("test", pair.getPrivate(), "test-pass".toCharArray(), new java.security.cert.Certificate[]{certificate});
        Path file = temp.resolve("test.p12");
        try (var out = Files.newOutputStream(file)) { store.store(out, "test-pass".toCharArray()); }
        var keys = new SigningKeyProvider(new FileSystemResource(file), "test-pass", "test");
        signing = new DocumentSigningService(new PdfSignatureService(keys), new DocxSignatureService(keys),
                new XmlSignatureService(keys), keys, validator);
    }

    static byte[] pdf() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4)); doc.save(out); return out.toByteArray();
        }
    }
    static byte[] docx() throws Exception {
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Thư bảo lãnh điện tử");
            doc.createTable(2,2).getRow(0).getCell(0).setText("Nội dung gốc");
            doc.write(out); return out.toByteArray();
        }
    }
    static byte[] xlsx() throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var row = book.createSheet("Bảo lãnh").createRow(0);
            row.createCell(0).setCellValue(100); row.createCell(1).setCellFormula("A1*2");
            book.createSheet("Thông tin").createRow(0).createCell(0).setCellValue("Tiếng Việt");
            book.write(out); return out.toByteArray();
        }
    }
    @Test void pdfSignsAndRejectsChangedDocument() throws Exception {
        byte[] signed = signing.signAndVerify(pdf(), "pdf");
        assertThrows(BadRequestException.class, () -> validator.validate(signed, "pdf", false));
        var changed = new ByteArrayOutputStream();
        try (var doc = Loader.loadPDF(signed)) { doc.addPage(new PDPage()); doc.saveIncremental(changed); }
        assertThrows(Exception.class, () -> signing.verify(changed.toByteArray(), "pdf"));
    }
    @Test void docxAndXlsxRoundTripAndDetectTampering() throws Exception {
        for (String format : List.of("docx", "xlsx")) {
            byte[] signed = signing.signAndVerify(format.equals("docx") ? docx() : xlsx(), format);
            assertThrows(BadRequestException.class, () -> validator.validate(signed, format, false));
            var changed = new ByteArrayOutputStream();
            try (var pkg = OPCPackage.open(new ByteArrayInputStream(signed))) {
                var part = pkg.getPart(PackagingURIHelper.createPartName(format.equals("docx") ? "/word/document.xml" : "/xl/worksheets/sheet1.xml"));
                byte[] bytes;
                try (var in = part.getInputStream()) { bytes = in.readAllBytes(); }
                String xml = new String(bytes, StandardCharsets.UTF_8);
                xml = format.equals("docx") ? xml.replace("Nội dung gốc", "Nội dung sửa") : xml.replace("100", "999");
                try (var out = part.getOutputStream()) { out.write(xml.getBytes(StandardCharsets.UTF_8)); }
                pkg.save(changed);
            }
            assertThrows(Exception.class, () -> signing.verify(changed.toByteArray(), format));
        }
    }
    @Test void xmlRoundTripAndTampering() throws Exception {
        byte[] signed = signing.signAndVerify("<hoSo xmlns='urn:test'><soTien>100</soTien></hoSo>".getBytes(StandardCharsets.UTF_8), "xml");
        assertThrows(BadRequestException.class, () -> validator.validate(signed, "xml", false));
        byte[] changed = new String(signed, StandardCharsets.UTF_8).replace(">100<", ">999<").getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> signing.verify(changed, "xml"));
    }
    @Test void rejectsXmlDtdExternalEntitiesAndMalformedDocuments() {
        for (String xml : List.of("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><a>&x;</a>", "<a>", ""))
            assertThrows(BadRequestException.class, () -> validator.validate(xml.getBytes(StandardCharsets.UTF_8), "xml", false));
    }
    @Test void acceptsLegacyXlsAndRejectsWrongExtensions() throws Exception {
        try (var book = new HSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            book.createSheet().createRow(0).createCell(0).setCellValue("Bảo lãnh"); book.write(out);
            validator.validate(out.toByteArray(), "xls", false);
            assertThrows(BadRequestException.class, () -> validator.validate(out.toByteArray(), "xlsx", false));
        }
        assertEquals("docx", DocumentValidator.extension("FILE.DOCX"));
        assertThrows(BadRequestException.class, () -> validator.validate(pdf(), "docx", false));
        assertThrows(BadRequestException.class, () -> validator.validate(docx(), "xlsx", false));
        assertThrows(BadRequestException.class, () -> validator.validate(new byte[]{1}, "exe", false));
    }
    @Test void rejectsMacroAndEmbeddedParts() throws Exception {
        for (String name : List.of("/word/vbaProject.bin", "/word/embeddings/oleObject1.bin")) {
            var bytes = new ByteArrayOutputStream();
            try (var pkg = OPCPackage.open(new ByteArrayInputStream(docx()))) {
                var part = pkg.createPart(PackagingURIHelper.createPartName(name), "application/octet-stream");
                try (var out = part.getOutputStream()) { out.write(1); }
                pkg.save(bytes);
            }
            assertThrows(BadRequestException.class, () -> validator.validate(bytes.toByteArray(), "docx", false));
        }
    }

    @Test void rejectsZipBombAndEncryptedOffice() throws Exception {
        byte[] expanded;
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("A".repeat(2_000_000));
            doc.write(out); expanded = out.toByteArray();
        }
        assertThrows(BadRequestException.class, () -> validator.validate(expanded, "docx", false));
        byte[] encrypted;
        org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword("VelvetSweatshop");
        try (var book = new HSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            book.createSheet(); book.write(out); encrypted = out.toByteArray();
        } finally { org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(null); }
        assertThrows(BadRequestException.class, () -> validator.validate(encrypted, "xls", false));
    }

    @Test void rejectsPdfJavaScript() throws Exception {
        byte[] bytes;
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.getDocumentCatalog().setOpenAction(new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("app.alert('test')"));
            doc.save(out); bytes = out.toByteArray();
        }
        assertThrows(BadRequestException.class, () -> validator.validate(bytes, "pdf", false));
    }

    @Test void emptyOleObjectPoolIsHarmlessButEmbeddedObjectsAreRejected() throws Exception {
        byte[] legacy;
        try (var book = new HSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            book.createSheet(); book.write(out); legacy = out.toByteArray();
        }
        try (var fs = new org.apache.poi.poifs.filesystem.POIFSFileSystem(new ByteArrayInputStream(legacy))) {
            var pool = fs.getRoot().createDirectory("ObjectPool");
            var empty = new ByteArrayOutputStream(); fs.writeFilesystem(empty);
            validator.validate(empty.toByteArray(), "xls", false);
            pool.createDocument("embedded", new ByteArrayInputStream(new byte[]{1}));
            var embedded = new ByteArrayOutputStream(); fs.writeFilesystem(embedded);
            assertThrows(BadRequestException.class, () -> validator.validate(embedded.toByteArray(), "xls", false));
        }
    }
}
