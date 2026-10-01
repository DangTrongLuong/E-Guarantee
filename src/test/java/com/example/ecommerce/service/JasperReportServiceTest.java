package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.GuaranteeResponse;
import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JasperReportServiceTest {

    @Autowired
    private JasperReportService jasperReportService;

    @Test
    void testGenerateGuaranteePdf() {
        GuaranteeResponse response = GuaranteeResponse.builder()
                .id("GR-2026-000002")
                .customerCif("0123456789")
                .customerName("SOMA")
                .taxCode("soma@gmail.com")
                .customerAddress("Hà Nội")
                .guaranteeType(GuaranteeType.BID_BOND)
                .guaranteeAmount(new BigDecimal("1121212111"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(1))
                .tenderNumber("BR-22")
                .relatedContractNumber("hd-11")
                .referenceNumber("001-11")
                .beneficiaryName("Tran Tuan Minh")
                .beneficiaryAddress("Thai Binh")
                .contactEmail("minh@gmail.com")
                .phoneNumber("0932873743")
                .purpose("tét")
                .status(GuaranteeStatus.DRAFT)
                .build();

        try {
            byte[] pdfBytes = jasperReportService.generateGuaranteePdf(response);
            assertNotNull(pdfBytes);
            assertTrue(pdfBytes.length > 0);
            assertEquals('%', (char) pdfBytes[0]);
            assertEquals('P', (char) pdfBytes[1]);
            assertEquals('D', (char) pdfBytes[2]);
            assertEquals('F', (char) pdfBytes[3]);
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            fail("PDF Generation failed: " + e.getMessage());
        }
    }
}
