package com.leave_management_system.leave_management_system.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidStatusValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidStatus {
    String message() default "Status must be either ACTIVE or INACTIVE";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String[] allowedValues() default {"ACTIVE", "INACTIVE"};
}
