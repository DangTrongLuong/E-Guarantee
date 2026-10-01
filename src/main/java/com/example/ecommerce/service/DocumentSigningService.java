package com.example.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.springframework.stereotype.Service;
import java.io.*;
import java.security.SignatureException;

@Service
@RequiredArgsConstructor
public class DocumentSigningService {
    private final PdfSignatureService pdf;
    private final DocxSignatureService office;
    private final XmlSignatureService xml;
    private final SigningKeyProvider keys;
    private final DocumentValidator validator;

    public byte[] signAndVerify(byte[] input, String format) throws Exception {
        validator.validate(input, format, false);
        var out = new ByteArrayOutputStream();
        byte[] signed;
        switch (format) {
            case "pdf" -> { pdf.signPdf(new ByteArrayInputStream(input), out); signed = out.toByteArray(); }
            case "docx", "xlsx" -> { office.signDocx(new ByteArrayInputStream(input), out); signed = out.toByteArray(); }
            case "xml" -> signed = xml.sign(input);
            default -> throw new IllegalArgumentException("Conversion and confirmation required for " + format);
        }
        validator.validate(signed, format, true);
        verify(signed, format);
        return signed;
    }

    public void verify(byte[] signed, String format) throws Exception {
        switch (format) {
            case "pdf" -> {
                try (var document = Loader.loadPDF(signed)) {
                    var signatures = document.getSignatureDictionaries();
                    if (signatures.size() != 1) throw new SignatureException("Expected one PDF signature");
                    var signature = signatures.getFirst();
                    int[] range = signature.getByteRange();
                    if (range.length != 4 || range[0] != 0 || range[1] <= 0 || range[2] <= range[1]
                            || range[3] < 0 || (long) range[2] + range[3] != signed.length)
                        throw new SignatureException("PDF signature does not cover the complete document");
                    var cms = new CMSSignedData(new CMSProcessableByteArray(signature.getSignedContent(signed)),
                            signature.getContents(signed));
                    var signers = cms.getSignerInfos().getSigners();
                    if (signers.size() != 1) throw new SignatureException("Expected one CMS signer");
                    if (!signers.iterator().next().verify(new JcaSimpleSignerInfoVerifierBuilder().build(keys.get().certificate())))
                        throw new SignatureException("PDF signature verification failed");
                }
            }
            case "docx", "xlsx" -> office.verify(signed);
            case "xml" -> xml.verify(signed);
            default -> throw new IllegalArgumentException("Unsupported signature format");
        }
    }
}
