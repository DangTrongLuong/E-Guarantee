package com.example.ecommerce.validation;

import java.time.LocalDate;

public interface DateRangeAware {
    LocalDate getEffectiveDate();
    LocalDate getExpiryDate();
}
