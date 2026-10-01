package com.example.ecommerce.service;

import com.example.ecommerce.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.poifs.filesystem.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.crypto.dsig.XMLSignature;

@Component
public class DocumentValidator {
    public static final long MAX_BYTES = 100 * 1024 * 1024L;
    public static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx", "xls", "xlsx", "xml");

    public static String extension(String name) {
        if (name == null || name.lastIndexOf('.') < 0) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public void validate(MultipartFile file) {
        try { validate(file.getBytes(), extension(file.getOriginalFilename()), false); }
        catch (IOException e) { throw new BadRequestException("Không đọc được file upload"); }
    }
    public void validate(byte[] bytes, String format, boolean allowSignature) {
        if (!ALLOWED.contains(format)) throw new BadRequestException("Chỉ hỗ trợ pdf, doc, docx, xls, xlsx, xml");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new BadRequestException("File rỗng hoặc vượt giới hạn 100MB");
        try {
            switch (format) {
                case "pdf" -> {
                    if (bytes.length < 5 || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
                        throw new IOException("Invalid PDF header");
                    try (var doc = Loader.loadPDF(bytes)) {
                        if (doc.isEncrypted() || doc.getNumberOfPages() == 0) throw new IOException("Encrypted or empty PDF");
                        if (!allowSignature && !doc.getSignatureDictionaries().isEmpty()) throw new IOException("Already signed");
                        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                        inspectPdf(doc.getDocumentCatalog().getCOSObject(), seen, 0);
                    }
                }
                case "doc", "xls" -> validateLegacy(bytes, format);
                case "docx", "xlsx" -> validateOffice(bytes, format, allowSignature);
                case "xml" -> {
                    var doc = SecureXml.parse(bytes);
                    if (!allowSignature && doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").getLength() > 0)
                        throw new IOException("Already signed XML");
                }
                default -> throw new IOException("Unsupported format");
            }
        } catch (Exception e) {
            throw new BadRequestException("File không hợp lệ, sai định dạng, đã ký, mã hóa hoặc có nội dung không được phép: " + format);
        }
    }
    private void inspectPdf(COSBase base, Set<COSBase> seen, int depth) throws IOException {
        if (base == null || !seen.add(base)) return;
        if (depth > 100 || seen.size() > 100000) throw new IOException("PDF structure too complex");
        if (base instanceof COSObject object) inspectPdf(object.getObject(), seen, depth + 1);
        else if (base instanceof COSDictionary dictionary) {
            for (var key : dictionary.keySet()) {
                if (Set.of("JS", "JavaScript", "EmbeddedFiles", "EF", "RichMediaContent", "XFA").contains(key.getName()))
                    throw new IOException("Active or embedded PDF content");
                inspectPdf(dictionary.getItem(key), seen, depth + 1);
            }
            if ("Launch".equals(dictionary.getNameAsString(COSName.S))) throw new IOException("PDF launch action");
        } else if (base instanceof COSArray array) for (var value : array) inspectPdf(value, seen, depth + 1);
    }
    private void inspectOle(DirectoryEntry dir) throws IOException {
        for (Entry entry : dir) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            boolean embeddedObjects = name.equals("objectpool")
                    && (!(entry instanceof DirectoryEntry pool) || pool.getEntryCount() > 0);
            if (name.contains("vba") || name.contains("macro") || embeddedObjects
                    || name.startsWith("mbd") || name.equals("encryptedpackage") || name.contains("signature"))
                throw new IOException("OLE macro, encryption or embedded object");
            if (entry instanceof DirectoryEntry child) inspectOle(child);
        }
    }
    private void validateLegacy(byte[] bytes, String format) throws Exception {
        try (var fs = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            inspectOle(fs.getRoot());
            if (format.equals("doc")) {
                try (var stream = fs.createDocumentInputStream("WordDocument")) {
                    byte[] header = stream.readNBytes(12);
                    if (header.length < 12 || ((header[11] & 0x81) != 0)) throw new IOException("Encrypted DOC");
                }
                try (var doc = new HWPFDocument(fs)) { doc.getRange(); }
            } else {
                String name = fs.getRoot().hasEntry("Workbook") ? "Workbook" : "Book";
                try (var stream = fs.createDocumentInputStream(name)) {
                    byte[] workbook = stream.readAllBytes();
                    int offset = 0;
                    while (offset + 4 <= workbook.length) {
                        int id = (workbook[offset] & 255) | (workbook[offset+1] & 255) << 8;
                        int len = (workbook[offset+2] & 255) | (workbook[offset+3] & 255) << 8;
                        // FILEPASS, OBPROJ, EXTERNNAME: encryption, VBA or external/OLE links.
                        if (id == 0x2f || id == 0xd3 || id == 0x23) throw new IOException("Unsafe XLS record");
                        offset += 4 + len;
                        if (offset > workbook.length) throw new IOException("Truncated XLS record");
                    }
                }
                try (var workbook = new HSSFWorkbook(fs)) { workbook.getNumberOfSheets(); }
            }
        }
    }
    private void validateOffice(byte[] bytes, String format, boolean allowSignature) throws Exception {
        try (var pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            checkRelationships(pkg);
            for (PackagePart part : pkg.getParts()) {
                String name = part.getPartName().getName().toLowerCase(Locale.ROOT);
                String type = part.getContentType().toLowerCase(Locale.ROOT);
                if (name.contains("vba") || name.contains("/embeddings/") || name.contains("/externallinks/")
                        || type.contains("macroenabled") || type.contains("activex") || type.contains("oleobject"))
                    throw new IOException("Unsafe OOXML content");
                if (!allowSignature && name.startsWith("/_xmlsignatures/")) throw new IOException("Already signed OOXML");
                if (!part.isRelationshipPart()) checkRelationships(part);
            }
            if (format.equals("docx")) {
                if (!pkg.containPart(PackagingURIHelper.createPartName("/word/document.xml"))) throw new IOException("Not DOCX");
                try (var doc = new XWPFDocument(pkg)) { doc.getParagraphs(); }
            } else {
                if (!pkg.containPart(PackagingURIHelper.createPartName("/xl/workbook.xml"))) throw new IOException("Not XLSX");
                try (var workbook = new XSSFWorkbook(pkg)) { workbook.getNumberOfSheets(); }
            }
        }
    }
    private void checkRelationships(RelationshipSource source) throws Exception {
        for (var rel : source.getRelationships()) {
            if (rel.getTargetMode() == TargetMode.EXTERNAL && !rel.getRelationshipType().endsWith("/hyperlink"))
                throw new IOException("External document relationship");
        }
    }
}
