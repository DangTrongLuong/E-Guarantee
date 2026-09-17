package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GuaranteeSummaryResponse {
    private String id;
    private String customerCif;
    private String customerName;
    private String taxCode;
    private GuaranteeStatus status;
    private GuaranteeType guaranteeType;
    private BigDecimal guaranteeAmount;
    private Currency currency;
    private LocalDateTime createdDate;
}
