package com.example.ecommerce.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Calendar;

@Slf4j
@Service
public class PdfSignatureService implements SignatureInterface {

    private final SigningKeyProvider keyProvider;

    public PdfSignatureService(SigningKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Hàm chính để ký PDF
     * @param inputPdf Stream chứa file PDF đầu vào
     * @param outputPdf Stream để ghi file PDF đầu ra (đã ký)
     */
    public void signPdf(InputStream inputPdf, OutputStream outputPdf) throws Exception {
        keyProvider.get();

        try (RandomAccessReadBuffer source = new RandomAccessReadBuffer(inputPdf);
             PDDocument document = Loader.loadPDF(source)) {
            // Khởi tạo đối tượng Chữ ký (PDSignature)
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("eGuarantee System");
            signature.setLocation("Hanoi, Vietnam");
            signature.setReason("Phê duyệt Thư Bảo Lãnh Điện Tử");
            signature.setSignDate(Calendar.getInstance());

            // Thêm chữ ký vào PDF (Lưu ý: tham số this là class hiện tại implement SignatureInterface)
            document.addSignature(signature, this);

            // Lưu file PDF ra Output Stream
            document.saveIncremental(outputPdf);
        }
    }

    /**
     * Interface Method của PDFBox để thực hiện Hash và Ký
     * Tại đây chúng ta dùng thuật toán SHA256withRSA qua thư viện BouncyCastle
     */
    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            var material = keyProvider.get();
            // Cấu hình thuật toán SHA256withRSA
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .build(material.privateKey());

            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().build())
                            .build(signer, material.certificate()));
            
            gen.addCertificates(new JcaCertStore(material.chain()));

            // Đọc content của file PDF để băm (Hash)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = content.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }

            CMSProcessableByteArray msg = new CMSProcessableByteArray(baos.toByteArray());

            // Tạo CMS Signed Data
            CMSSignedData signedData = gen.generate(msg, false);

            return signedData.getEncoded();
        } catch (Exception e) {
            log.error("Lỗi trong quá trình tạo mã băm và mã hóa chữ ký (SHA256withRSA)", e);
            throw new IOException(e);
        }
    }
}
