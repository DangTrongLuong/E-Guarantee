package com.example.ecommerce.entity;

import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import com.example.ecommerce.validation.DateRangeAware;
import com.example.ecommerce.validation.TenderNumberAware;
import com.example.ecommerce.validation.ValidExpiryDate;
import com.example.ecommerce.validation.ValidTenderNumber;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Check;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "guarantee_requests")
@Check(name = "chk_gr_expiry_after_effective", constraints = "expiry_date >= effective_date")
@Check(
        name = "chk_gr_tender_number_required",
        constraints = "guarantee_type <> 'BID_BOND' OR (tender_number IS NOT NULL AND CHAR_LENGTH(TRIM(tender_number)) > 0)"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"customer", "createdBy", "updatedBy"})
@ValidExpiryDate
@ValidTenderNumber
public class GuaranteeRequest implements Serializable, DateRangeAware, TenderNumberAware {

    @Id
    @NotBlank
    @Size(max = 20)
    @Column(name = "id", length = 20, nullable = false, updatable = false)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_cif", referencedColumnName = "cif", nullable = false)
    private Customer customer;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "guarantee_type", length = 30, nullable = false)
    private GuaranteeType guaranteeType;

    @NotNull
    @Column(name = "guarantee_amount", precision = 15, scale = 2, nullable = false)
    @Check(name = "chk_gr_amount_range", constraints = "guarantee_amount > 0 AND guarantee_amount <= 1000000000000")
    private BigDecimal guaranteeAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private Currency currency = Currency.VND;

    @NotNull
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @NotNull
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Size(max = 100)
    @Column(name = "tender_number", length = 100, nullable = true)
    private String tenderNumber;

    @Size(max = 100)
    @Column(name = "related_contract_number", length = 100, nullable = true)
    private String relatedContractNumber;

    @Size(max = 100)
    @Column(name = "reference_number", length = 100, nullable = true)
    private String referenceNumber;

    @Size(max = 1000)
    @Column(name = "purpose", length = 1000, nullable = true)
    private String purpose;

    @NotBlank
    @Size(max = 255)
    @Column(name = "beneficiary_name", length = 255, nullable = false)
    private String beneficiaryName;

    @Size(max = 500)
    @Column(name = "beneficiary_address", length = 500, nullable = true)
    private String beneficiaryAddress;

    @NotBlank
    @Size(max = 255)
    @Column(name = "contact_email", length = 255, nullable = false)
    private String contactEmail;

    @Size(max = 15)
    @Column(name = "phone_number", length = 15, nullable = true)
    @Check(name = "chk_gr_phone_number_format", constraints = "phone_number IS NULL OR phone_number REGEXP '^[0-9]{9,15}$'")
    private String phoneNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private GuaranteeStatus status = GuaranteeStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by", referencedColumnName = "username", nullable = true, updatable = false)
    private User createdBy;
//    @Size(max = 50)
//    @Column(name = "created_by", length = 50, nullable = true, updatable = false)
//    private String createdBy;

    @NotNull
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "updated_by", referencedColumnName = "username", nullable = true)
    private User updatedBy;
//    @Size(max = 50)
//    @Column(name = "updated_by", length = 50, nullable = true)
//    private String updatedBy;

    @Column(name = "updated_date", nullable = true)
    private LocalDateTime updatedDate;

    @PrePersist
    protected void onCreate() {
        if (this.createdDate == null) {
            this.createdDate = LocalDateTime.now();
        }
        if (this.currency == null) {
            this.currency = Currency.VND;
        }
        if (this.status == null) {
            this.status = GuaranteeStatus.DRAFT;
        }
    }
}

