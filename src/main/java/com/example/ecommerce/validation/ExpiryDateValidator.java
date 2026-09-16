package com.example.ecommerce.validation;


import com.example.ecommerce.entity.GuaranteeRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


public class ExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, GuaranteeRequest> {

    @Override
    public boolean isValid(GuaranteeRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getEffectiveDate() == null || request.getExpiryDate() == null) {
            return true;
        }
        boolean valid = request.getExpiryDate().isAfter(request.getEffectiveDate());
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Ngày hết hạn phải sau Ngày hiệu lực !")
                    .addPropertyNode("expiryDate")
                    .addConstraintViolation();
        }
        return valid;
    }
}
