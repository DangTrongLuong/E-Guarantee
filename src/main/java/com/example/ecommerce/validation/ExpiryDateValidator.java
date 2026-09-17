package com.example.ecommerce.validation;


import com.example.ecommerce.entity.GuaranteeRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


public class ExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, DateRangeAware> {

    @Override
    public boolean isValid(DateRangeAware target, ConstraintValidatorContext context) {
        if (target == null || target.getEffectiveDate() == null || target.getExpiryDate() == null) {
            return true;
        }
        boolean valid = target.getExpiryDate().isAfter(target.getEffectiveDate());
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Ngày hết hạn phải sau Ngày hiệu lực !")
                    .addPropertyNode("expiryDate")
                    .addConstraintViolation();
        }
        return valid;
    }
}
