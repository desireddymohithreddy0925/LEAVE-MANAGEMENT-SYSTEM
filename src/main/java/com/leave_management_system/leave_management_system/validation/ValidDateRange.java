package com.leave_management_system.leave_management_system.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidDateRangeValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateRange {
    String message() default "Invalid date range: end date must be on or after start date and within the same calendar year";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String startDateField() default "startDate";
    String endDateField() default "endDate";
    boolean requireSameYear() default true;
}
