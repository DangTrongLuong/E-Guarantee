package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.GuaranteeResponse;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import com.example.ecommerce.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JasperReportService {

    private static final String JASPER_TEMPLATE_PATH = "jasper/DetalGuarantee.jrxml";

    public byte[] generateGuaranteePdf(GuaranteeResponse guarantee) {
        if (guarantee == null) {
            throw new BadRequestException("Thông tin bảo lãnh không được để trống!");
        }

        Map<String, Object> parameters = prepareParametersFromResponse(guarantee);
        return generatePdfFromTemplate(parameters);
    }

    public byte[] generatePdfFromCustomParams(Map<String, Object> customParams) {
        Map<String, Object> parameters = new HashMap<>();
        if (customParams != null) {
            customParams.forEach((key, value) -> {
                if (value != null) {
                    parameters.put(key, value.toString());
                }
            });
        }
        return generatePdfFromTemplate(parameters);
    }

    private byte[] generatePdfFromTemplate(Map<String, Object> parameters) {
        try {
            System.setProperty("net.sf.jasperreports.compiler.xml.validation", "false");
            System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
            ClassPathResource resource = new ClassPathResource(JASPER_TEMPLATE_PATH);
            if (!resource.exists()) {
                throw new BadRequestException("Không tìm thấy file mẫu báo cáo Jasper: " + JASPER_TEMPLATE_PATH);
            }

            try (InputStream inputStream = resource.getInputStream()) {
                net.sf.jasperreports.engine.design.JasperDesign jasperDesign = net.sf.jasperreports.engine.xml.JRXmlLoader.load(inputStream);
                JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);
                JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
                byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);

                log.info("Gen file PDF báo cáo bảo lãnh thành công (dung lượng: {} bytes)", pdfBytes.length);
                return pdfBytes;
            }
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Lỗi khi sinh file PDF từ mẫu Jasper: ", cause);
            throw new BadRequestException("Lỗi hệ thống khi xuất file PDF báo cáo: " + cause.toString());
        }
    }

    private Map<String, Object> prepareParametersFromResponse(GuaranteeResponse guarantee) {
        Map<String, Object> params = new HashMap<>();

        params.put("ID", defaultString(guarantee.getId(), "N/A"));
        params.put("STATUS", formatStatus(guarantee.getStatus()));
        params.put("CUSTOMER_NAME", defaultString(guarantee.getCustomerName(), "N/A"));
        params.put("CUSTOMER_CIF", defaultString(guarantee.getCustomerCif(), "N/A"));
        params.put("TAX_CODE", defaultString(guarantee.getTaxCode(), "N/A"));
        params.put("CUSTOMER_ADDRESS", defaultString(guarantee.getCustomerAddress(), "N/A"));
        params.put("GUARANTEE_TYPE", formatGuaranteeType(guarantee.getGuaranteeType()));
        params.put("GUARANTEE_AMOUNT", formatAmount(guarantee.getGuaranteeAmount(), guarantee.getCurrency() != null ? guarantee.getCurrency().name() : "VND"));
        params.put("EFFECTIVE_DATE", guarantee.getEffectiveDate() != null ? guarantee.getEffectiveDate().toString() : "N/A");
        params.put("EXPIRY_DATE", guarantee.getExpiryDate() != null ? guarantee.getExpiryDate().toString() : "N/A");
        params.put("GUARANTEE_DAYS", guarantee.getGuaranteeDays() != null ? guarantee.getGuaranteeDays() + " ngày" : "N/A");
        params.put("TENDER_NUMBER", defaultString(guarantee.getTenderNumber(), "-"));
        params.put("RELATED_CONTRACT_NUMBER", defaultString(guarantee.getRelatedContractNumber(), "-"));
        params.put("REFERENCE_NUMBER", defaultString(guarantee.getReferenceNumber(), "-"));
        params.put("BENEFICIARY_NAME", defaultString(guarantee.getBeneficiaryName(), "N/A"));
        params.put("BENEFICIARY_ADDRESS", defaultString(guarantee.getBeneficiaryAddress(), "N/A"));
        params.put("CONTACT_EMAIL", defaultString(guarantee.getContactEmail(), "N/A"));
        params.put("PHONE_NUMBER", defaultString(guarantee.getPhoneNumber(), "N/A"));
        params.put("PURPOSE", defaultString(guarantee.getPurpose(), "N/A"));

        return params;
    }

    private String formatStatus(GuaranteeStatus status) {
        if (status == null) return "Bản nháp";
        return switch (status) {
            case DRAFT -> "Bản nháp";
            case PENDING_APPROVAL -> "Chờ phê duyệt";
            case APPROVED -> "Đã phê duyệt";
            case REJECTED -> "Từ chối";
            default -> "Bản nháp";
        };
    }

    private String formatGuaranteeType(GuaranteeType type) {
        if (type == null) return "Bảo lãnh khác";
        return switch (type) {
            case BID_BOND -> "Bảo lãnh dự thầu";
            case PERFORMANCE -> "Bảo lãnh thực hiện hợp đồng";
            case ADVANCE_PAYMENT -> "Bảo lãnh tạm ứng";
            case PAYMENT -> "Bảo lãnh thanh toán";
            case OTHER -> "Bảo lãnh khác";
            default -> "Bảo lãnh khác";
        };
    }

    private String formatAmount(java.math.BigDecimal amount, String currency) {
        if (amount == null) return "0 " + currency;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(amount) + " " + currency;
    }

    private String defaultString(String val, String def) {
        return (val != null && !val.isBlank()) ? val.trim() : def;
    }
}
