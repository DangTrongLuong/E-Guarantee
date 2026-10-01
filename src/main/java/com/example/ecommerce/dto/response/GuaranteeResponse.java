package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuaranteeResponse {
    private String id;
    private String customerCif;
    private String customerName;
    private String taxCode;
    private String customerAddress;
    private GuaranteeType guaranteeType;
    private BigDecimal guaranteeAmount;
    private Currency currency;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private Long guaranteeDays;
    private String tenderNumber;
    private String relatedContractNumber;
    private String referenceNumber;
    private String purpose;
    private String beneficiaryName;
    private String beneficiaryAddress;
    private String contactEmail;
    private String phoneNumber;
    private GuaranteeStatus status;
    private com.example.ecommerce.enums.SignatureStatus signatureStatus;
    private String createdBy;
    private String createdByFullName;
    private LocalDateTime createdDate;
    private String updatedBy;
    private String updatedByFullName;
    private LocalDateTime updatedDate;
    private List<GuaranteeFileResponse> files;

    public Long getGuaranteeDays() {
        if (effectiveDate != null && expiryDate != null) {
            return ChronoUnit.DAYS.between(effectiveDate, expiryDate);
        }
        return null;
    }
}
