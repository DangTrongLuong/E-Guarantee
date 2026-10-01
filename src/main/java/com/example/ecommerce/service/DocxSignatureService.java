package com.example.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.crypt.dsig.*;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.Date;

/** ECMA-376 signatures for both DOCX and XLSX packages. */
@Service
@RequiredArgsConstructor
public class DocxSignatureService {
    private final SigningKeyProvider keyProvider;

    public void signDocx(InputStream input, OutputStream output) throws Exception {
        var material = keyProvider.get();
        byte[] normalized;
        try (OPCPackage pkg = OPCPackage.open(input)) {
            var buffer = new ByteArrayOutputStream();
            pkg.save(buffer);
            normalized = buffer.toByteArray();
        }
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(normalized))) {
            SignatureConfig config = new SignatureConfig();
            config.setKey(material.privateKey());
            config.setSigningCertificateChain(material.chain());
            config.setDigestAlgo(HashAlgorithm.sha256);
            config.setExecutionTime(new Date());
            SignatureInfo info = new SignatureInfo();
            info.setOpcPackage(pkg);
            info.setSignatureConfig(config);
            info.confirmSignature();
            pkg.save(output);
        }
    }

    public void verify(byte[] bytes) throws Exception {
        var material = keyProvider.get();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            SignatureInfo info = new SignatureInfo();
            info.setOpcPackage(pkg);
            info.setSignatureConfig(new SignatureConfig());
            int count = 0;
            for (SignaturePart part : info.getSignatureParts()) {
                count++;
                if (!part.validate() || !part.getSigner().getPublicKey().equals(material.certificate().getPublicKey()))
                    throw new java.security.SignatureException("Invalid OOXML signature or signer");
            }
            if (count != 1) throw new java.security.SignatureException("Expected exactly one OOXML signature");
        }
    }
}
