package com.example.ecommerce.validation;

import com.example.ecommerce.entity.ProcessingHistory;
import com.example.ecommerce.enums.Action;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


public class RejectCommentValidator implements ConstraintValidator<ValidRejectComment, ProcessingHistory> {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 500;

    @Override
    public boolean isValid(ProcessingHistory history, ConstraintValidatorContext context) {
        if (history == null) {
            return true;
        }
        if (history.getAction() == Action.REJECT) {
            String comment = history.getComment();
            boolean valid = comment != null
                    && comment.length() >= MIN_LENGTH
                    && comment.length() <= MAX_LENGTH;
            if (!valid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "comment là bắt buộc và phải có độ dài 10-500 ký tự khi action = REJECT")
                        .addPropertyNode("comment")
                        .addConstraintViolation();
            }
            return valid;
        }
        return true;
    }
}
