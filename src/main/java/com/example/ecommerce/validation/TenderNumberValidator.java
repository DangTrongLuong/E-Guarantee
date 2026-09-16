package com.example.ecommerce.validation;


import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.enums.GuaranteeType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


public class TenderNumberValidator implements ConstraintValidator<ValidTenderNumber, GuaranteeRequest> {

    @Override
    public boolean isValid(GuaranteeRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        if (request.getGuaranteeType() == GuaranteeType.BID_BOND) {
            boolean valid = request.getTenderNumber() != null && !request.getTenderNumber().isBlank();
            if (!valid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Số hiệu gói thầu là bắt buộc là bắt buộc khi guarantee_type = BID_BOND")
                        .addPropertyNode("tenderNumber")
                        .addConstraintViolation();
            }
            return valid;
        }
        return true;
    }
}

