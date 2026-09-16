package com.example.ecommerce.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Documented
@Constraint(validatedBy = RejectCommentValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRejectComment {
    String message() default "comment là bắt buộc và phải có độ dài 10-500 ký tự khi action = REJECT";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
