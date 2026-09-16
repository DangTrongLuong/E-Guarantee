package com.example.ecommerce.validation;

import com.example.ecommerce.enums.GuaranteeType;

public interface TenderNumberAware {
    GuaranteeType getGuaranteeType();
    String getTenderNumber();
}
